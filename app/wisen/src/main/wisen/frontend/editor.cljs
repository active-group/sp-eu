(ns wisen.frontend.editor
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.change :as change]
            [wisen.common.change-api :as change-api]
            [wisen.frontend.default :as default]
            [wisen.frontend.osm :as osm]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.details :as details]
            [wisen.frontend.schemaorg :as schemaorg]
            [wisen.frontend.util :as util]
            [wisen.frontend.or-error :refer [success? success-value]]
            [wisen.frontend.schema :as schema]))

(def-record discard-edit-action
  [discard-edit-action-predicate
   discard-edit-action-index])

(defn- style-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    {:border "1px solid red"
     :color "red"}

    (edit-tree/added? marked)
    {:border "1px solid green"
     :color "green"}

    (edit-tree/same? marked)
    {:border "1px solid gray"}

    (edit-tree/changed? marked)
    {:border "1px solid orange"
     :color "orange"}))

(defn- icon-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    ds/minus-icon

    (edit-tree/added? marked)
    ds/plus-icon

    (edit-tree/same? marked)
    nil

    (edit-tree/changed? marked)
    ds/dot-icon))

(defn pprint [x]
  (dom/pre
   (with-out-str
     (cljs.pprint/pprint x))))

(c/defn-item ^:private before-after [before-item after-item]
  (c/with-state-as [state before-or-after :local ::after]
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"}}

     (c/focus lens/second
              (dom/div
               {:style {:display "flex"}}
               (dom/div {:style {:border-top "1px solid gray"
                                 :border-left "1px solid gray"
                                 :border-right "1px solid gray"
                                 :border-bottom (if (= before-or-after ::before)
                                                  "1px solid #eee"
                                                  "1px solid gray")
                                 :border-top-left-radius "4px"
                                 :padding "0.5ex 0.5em"
                                 :cursor "pointer"
                                 :color "red"
                                 :position "relative"
                                 :top "1px"
                                 :font-weight (if (= before-or-after ::before)
                                                "bold"
                                                "normal")}
                         :onClick (constantly ::before)}
                        "Before")
               (dom/div {:style {:border-top "1px solid gray"
                                 :border-right "1px solid gray"
                                 :border-bottom (if (= before-or-after ::after)
                                                  "1px solid #eee"
                                                  "1px solid gray")
                                 :border-top-right-radius "4px"
                                 :padding "0.5ex 0.5em"
                                 :cursor "pointer"
                                 :color "green"
                                 :position "relative"
                                 :top "1px"
                                 :font-weight (if (= before-or-after ::after)
                                                "bold"
                                                "normal")}
                         :onClick (constantly ::after)}
                        "After")))

     (dom/div
      {:style {:border "1px solid gray"
               :padding "1ex 1em"}}
      (c/focus lens/first
               (case before-or-after
                 ::before
                 before-item

                 ::after
                 after-item))))))

(defn- make-edit-tree-kind-lens [schema predicate]
  (fn
    ([etree]
     (cond
       (edit-tree/literal-string? etree)
       tree/literal-string

       (edit-tree/literal-decimal? etree)
       tree/literal-decimal

       (edit-tree/literal-boolean? etree)
       tree/literal-boolean

       (edit-tree/ref? etree)
       tree/ref

       (edit-tree/edit-node? etree)
       tree/node))
    ([etree kind]
     (if (= kind ((make-edit-tree-kind-lens schema predicate) etree))
       etree
       (edit-tree/make-added-edit-tree
        (default/default-tree-for-predicate-and-kind predicate kind))))))

(defn- label-for-kind [kind]
  (cond
    (= kind tree/literal-string)
    "String"

    (= kind tree/literal-decimal)
    "Decimal"

    (= kind tree/literal-boolean)
    "Boolean"

    (= kind tree/ref)
    "Node"

    (= kind tree/node)
    "Node"))

(defn- map-values [f m]
  (reduce (fn [acc [k v]]
            (assoc acc k (f v)))
          {}
          m))

(defn- edit-node-type
  "TODO: This must be developed closer to a spec."
  ([enode]
   (edit-tree/edit-tree-result-tree
    (edit-tree/node-object-for-predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" enode)))
  ([enode type]
   (if (= type (edit-node-type enode))
     enode
     (lens/overhaul
      enode
      edit-tree/edit-node-properties
      (fn [old-eprops]
        (let [old-eprops* (map-values (fn [metrees]
                                        (map (fn [metree]
                                               (if (edit-tree/can-delete? metree)
                                                 (edit-tree/mark-deleted metree)
                                                 metree))
                                             metrees))
                                      old-eprops)
              new-eprops (edit-tree/edit-node-properties
                          (edit-tree/make-added-edit-tree (default/default-node-for-type type)))]
          (merge-with
           (fn [old-metrees new-metrees]
             ;; set new for now (TODO)
             (distinct
              (if (= 1
                     (count old-metrees)
                     (count new-metrees))
                (let [old-metree (first old-metrees)
                      new-metree (first new-metrees)]
                  (cond
                    (edit-tree/deleted? old-metree)
                    [(edit-tree/make-maybe-changed
                      (edit-tree/deleted-original-value old-metree)
                      (edit-tree/added-result-value new-metree))]

                    (edit-tree/added? old-metree)
                    [old-metree new-metree]

                    (edit-tree/maybe-changed? old-metree)
                    [(edit-tree/maybe-changed-result-value old-metree
                                                           (edit-tree/added-result-value new-metree))]))
                ;; else choose new metrees (TODO
                new-metrees)))
           old-eprops*
           new-eprops)))))))

(declare edit-tree-component)

(defn component-for-predicate [predicate schema editable? editing?]
  (dom/div
   (c/focus (make-edit-tree-kind-lens schema predicate)
            (apply
             ds/select
             {:disabled (when-not editable? "disabled")}
             (map (fn [kind]
                    (forms/option {:value kind} (label-for-kind kind)))
                  (schema/kinds-for-predicate schema predicate))))
   (case predicate
     "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
     (c/with-state-as node
       (schema/label-for-type schema (edit-tree/edit-tree-result-tree node)))

     "http://schema.org/description"
     (c/focus edit-tree/literal-string-value
              (ds/textarea {:style {:width "100%"
                                    :min-height "6em"}
                            :disabled (when-not editable?
                                        "disabled")}))

     "http://schema.org/dayOfWeek"
     (c/focus edit-tree/tree-uri (ds/select
                                  {:disabled (when-not editable? "disabled")}
                                  (forms/option {:value "http://schema.org/Monday"} "Monday")
                                  (forms/option {:value "http://schema.org/Tuesday"} "Tuesday")
                                  (forms/option {:value "http://schema.org/Wednesday"} "Wednesday")
                                  (forms/option {:value "http://schema.org/Thursday"} "Thursday")
                                  (forms/option {:value "http://schema.org/Friday"} "Friday")
                                  (forms/option {:value "http://schema.org/Saturday"} "Saturday")
                                  (forms/option {:value "http://schema.org/Sunday"} "Sunday")
                                  ))

     "https://wisen.active-group.de/target-group"
     (c/focus edit-tree/literal-string-value
              (ds/select
               {:disabled (when-not editable? "disabled")}
               (forms/option {:value "elderly"} "Elderly")
               (forms/option {:value "queer"} "Queer")
               (forms/option {:value "immigrants"} "Immigrants")))

     (edit-tree-component
      schema
      (schema/types-for-predicate schema predicate)
      editable?
      editing?))))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (edit-tree/tree-uri (edit-tree/node-type node))))

(c/defn-item add-property-button [schema predicates]
  (c/with-state-as [node predicate :local schemaorg/default-predicate]
    (dom/div
     {:style {:display "flex"
              :gap "1em"}}
     (dom/div
      {:style {:display "flex"
               :border "1px solid #888"
               :border-radius "3px"}}
      (c/focus lens/second
               (apply
                ds/select
                {:style {:border 0
                         :border-right "1px solid #888"
                         :border-top-right-radius "0px"
                         :border-bottom-right-radius "0px"}}
                (map (fn [pred]
                       (forms/option {:value pred} (schema/label-for-predicate schema pred)))
                     predicates)))

      (ds/button-primary
       {:style {:padding "2px 12px"
                :color "#444"
                :background "#ddd"
                :font-weight "normal"}
        :onClick
        (fn [[node predicate] _]
          [(edit-tree/edit-node-add-property node predicate (default/default-tree-for-predicate schema predicate))
           predicate])}
       "Add property")))))

;; OSM

(defn- pr-osm-uri [uri]
  (dom/a {:href uri}
         "View on OpenStreetMap"))

(declare readonly-graph)

(c/defn-item osm-importer [schema osm-uri]
  (c/with-state-as graph
    (c/fragment

     (when (some? osm-uri)
       (util/load-json-ld-state
        (osm/osm-lookup-request osm-uri)))

     (when graph (readonly-graph schema graph)))))

(c/defn-item link-organization-with-osm-button [schema osm-uri close-action]
  (c/with-state-as [node local-state :local {:graph nil
                                             :osm-uri osm-uri
                                             :commit-osm-uri nil}]
    (dom/div
     (modal/padded
      (dom/h3 (if osm-uri "Update " "Link with ") "OpenStreetMap")

      (c/focus (lens/>> lens/second :osm-uri)
               (ds/input
                {:type "url"
                 :placeholder "https://www.openstreetmap.org/..."}))

      (c/focus (lens/>> lens/second :graph)
               (osm-importer schema (:commit-osm-uri local-state))))

     (modal/toolbar
      (ds/button-secondary {:onClick #(c/return :action close-action)}
                           "Cancel")

      (c/focus lens/second
               (ds/button-primary {:onClick (fn [ls]
                                              (assoc ls :commit-osm-uri (:osm-uri ls)))}
                                  "Ask OSM!"))

      (when (:graph local-state)
        (ds/button-primary
         {:onClick (fn [[node local-state]]
                     (let [place-node (first (edit-tree/graph->edit-trees (:graph local-state)))]
                       (assert (edit-tree/node? place-node))
                       (c/return :state [(osm/organization-do-link-osm
                                          node
                                          (:osm-uri local-state)
                                          place-node)
                                         (-> local-state
                                             (dissoc :graph)
                                             (dissoc :commit-osm-uri))]
                                 :action close-action)))}
         "Add properties as 'location'"))))))

;; LLM

(defn llm-query [prompt]
  (ajax/map-ok-response
   (ajax/POST "/describe" {:body prompt})
   :json-ld-string))

(defn- prepare-prompt [schema-type prompt]
  (str "I need a <" schema-type ">, " prompt))

(c/defn-item ask-ai [schema close-action]
  (c/with-state-as [node local-state :local {:graphs nil ;; prompt -> graph
                                             :commit-prompt nil
                                             :prompt "Just come up with something!"}]
    (let [prompt (:prompt local-state)
          commit-prompt (:commit-prompt local-state)
          current-graph (get (:graphs local-state) prompt)]

      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :gap "2ex"}}

       (modal/padded

        (dom/h3 "Ask an AI to fill out this form")

        (c/focus (lens/>> lens/second :prompt)
                 (ds/textarea))

        (when commit-prompt
          (c/focus (lens/>> lens/second :graphs (lens/member commit-prompt))
                   (util/load-json-ld-state (llm-query (prepare-prompt (edit-tree/tree-uri
                                                                        (edit-tree/node-type node))
                                                                       commit-prompt)))))

        (when current-graph
          #_(pr-str current-graph)
          (readonly-graph schema current-graph)))

       (modal/toolbar

        (ds/button-secondary {:onClick #(c/return :action close-action)}
                             "Cancel")

        (c/focus lens/second
                 (ds/button-primary {:onClick (fn [ls]
                                                (assoc ls :commit-prompt (:prompt ls)))}
                                    "Ask AI!"))

        (when (:graphs local-state)
          (ds/button-primary
           {:onClick (fn [[node local-state]]
                       (let [ai-node (first (edit-tree/graph->edit-trees current-graph))]
                         (assert (edit-tree/node? ai-node))
                         (c/return :state [#_(tree/merge node ai-node)
                                           ai-node
                                           {}]
                                   :action close-action)))}
           "Use these properties")))))))

;; ---

(declare the-circle)

(defn- set-properties [schema]
  (c/with-state-as node
    (dom/div {:style {:display "flex"
                      :gap "2em"
                      :align-items "center"}}

             (dom/div
              {:style {:display "flex"}}
              (add-property-button schema (schema/predicates-for-type schema (edit-tree/node-type node))))

             (dom/div
              {:style {:display "flex"
                       :gap "1em"}}

              (when (node-organization? node)
                (if-let [osm-uri (osm/node-osm-uri node)]

                  (dom/div
                   {:style {:display "flex"
                            :gap "1em"}}
                   (pr-osm-uri osm-uri)
                   (modal/modal-button "Update" #(link-organization-with-osm-button schema osm-uri %)))
                  (modal/modal-button "Link with OpenStreetMap" #(link-organization-with-osm-button schema nil %))))

              (modal/modal-button "Set properties with AI" (partial ask-ai schema))))))

(defn- set-reference [close-action]
  (c/with-state-as node
    (c/local-state
     (edit-tree/tree-uri node)
     (dom/div
      (modal/padded
       (dom/h3 "Set as reference to another node")

       (c/focus lens/second (ds/input {:size 80})))

      (modal/toolbar
       (ds/button-secondary {:onClick #(c/return :action close-action)}
                            "Cancel")
       (ds/button-primary {:onClick
                           (fn [[_ uri]]
                             (c/return :state [(edit-tree/make-ref uri) uri]))}
                          "Set reference"))))))

(defn- refresh-node-request [uri]
  (ajax/GET uri
            {:headers {:accept "application/ld+json"}}))

(c/defn-item ^:private refresh-node [uri]
  (util/load-json-ld
   (refresh-node-request uri)))

(defn- refresh-button []
  (c/with-state-as [node refresh? :local false]
    (c/fragment
     (c/focus lens/second
              (ds/button-primary {:onClick #(c/return :state true)}
                                 "Refresh"))
     (when refresh?
       (-> (refresh-node (edit-tree/tree-uri node))
           (c/handle-action (fn [[enode _] ac]
                              (if (success? ac)
                                (let [new-graph (success-value ac)
                                      new-node (first (tree/graph->trees new-graph))]
                                  [(edit-tree/set-edit-node-original enode new-node) false])
                                (assert false "TODO: implement error handling")))))))))

(defn- the-circle []
  (dom/div {:style {:border "1px solid #777"
                    :background "white"
                    :border-radius "100%"
                    :width "27px"
                    :height "27px"}}))

(c/defn-item property-object-component [schema predicate editable? force-editing? last?]
  (c/with-state-as marked-edit-trees
    (apply dom/div
           (map-indexed (fn [idx _]
                          (c/focus (lens/at-index idx)
                                   (c/with-state-as marked-edit-tree
                                     (dom/div
                                      {:style {:flex 1
                                               :display "flex"
                                               :position "relative"}}

                                      (when last?
                                        (dom/div {:style {:width "1px"
                                                          :border-left (if force-editing?
                                                                         "1px dashed gray"
                                                                         "1px solid #eee")
                                                          :height "100%"
                                                          :position "absolute"
                                                          :top "15px"
                                                          :z-index "5"
                                                          :left "-1px"
                                                          :background "#eee"}}))

                                      (dom/div
                                       {:style {:min-width "10em"
                                                :position "relative"
                                                :display "flex"
                                                #_#_:gap "1em"
                                                :align-items "flex-start"}}

                                       (dom/div
                                        {:style {:display "flex"
                                                 :margin "0 24px"}}
                                        (dom/div
                                         {:style (merge {:background "white"
                                                         :display "inline-flex"
                                                         :gap "0.3em"
                                                         :align-items "center"
                                                         :border (str "1px solid gray")
                                                         :padding "4px 12px"
                                                         :position "relative"
                                                         :z-index "5"}
                                                        (style-for-marked marked-edit-tree))}
                                         (icon-for-marked marked-edit-tree)
                                         (schema/label-for-predicate schema predicate))

                                        (when (edit-tree/can-discard-edit? marked-edit-tree)
                                          (ds/button-secondary
                                           {:style
                                            {:border-color "gray"
                                             :border-style "solid"
                                             :border-width "1px 1px 1px 0"
                                             :background "#ddd"
                                             :padding "4px 12px"
                                             :position "relative"
                                             :z-index "5"}
                                            :onClick
                                            #(c/return :action
                                                       (discard-edit-action
                                                        discard-edit-action-predicate
                                                        predicate
                                                        discard-edit-action-index
                                                        idx))}
                                           ds/x-icon)))

                                       (when (and force-editing?
                                                  (edit-tree/can-delete? marked-edit-tree))
                                         (ds/button-secondary {:onClick edit-tree/mark-deleted
                                                               :style {:font-size "25px"
                                                                       :font-weight "normal"
                                                                       :cursor "pointer"
                                                                       :z-index 5}}
                                                              "×")))

                                      (cond
                                        (edit-tree/deleted? marked-edit-tree)
                                        (c/focus edit-tree/deleted-original-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/added? marked-edit-tree)
                                        (c/focus edit-tree/added-result-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/same? marked-edit-tree)
                                        (c/focus edit-tree/maybe-changed-result-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/changed? marked-edit-tree)
                                        (before-after
                                         ;; before
                                         (c/focus edit-tree/maybe-changed-original-value
                                                  (component-for-predicate predicate schema false false))
                                         ;; after
                                         (c/focus edit-tree/maybe-changed-result-value
                                                  (component-for-predicate predicate schema editable? force-editing?))))))))

                        marked-edit-trees))))

(c/defn-item properties-component [schema editable? force-editing?]
  (c/with-state-as properties

    (apply
     dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :gap "2ex"}}

     (let [ks (sort schemaorg/compare-predicate (keys properties))]
       (map-indexed (fn [idx predicate]
                      (c/focus (lens/member predicate)
                               (let [last? (= idx (dec (count ks)))]
                                 (property-object-component schema predicate editable? force-editing? last?))))

                    ks)))))

(defn- node-component [schema editable? force-editing?]
  (c/with-state-as [node editing? :local force-editing?]

    (let [uri (edit-tree/node-uri node)]

      (dom/div

       (dom/div
        {:style {:color "#555"
                 :display "flex"
                 :align-items "center"
                 :gap "1em"}}
        (the-circle)
        (dom/span {:style {:margin-right "1em"}} uri)

        (when editing?
          (c/fragment
           (c/focus lens/first
                    (modal/modal-button "Set reference" set-reference))))

        (when (edit-tree/can-refresh? node)
          (c/focus lens/first (refresh-button)))

        (when editable?
          (c/focus lens/second
                   (ds/button-primary {:onClick not}
                                      (if editing? "Done" "Edit")))))

       (c/focus
        lens/first
        (dom/div

         (-> (c/focus edit-tree/edit-node-properties
                      (c/with-state-as eprops
                        (when-not (empty? eprops)
                          (dom/div
                           {:style {:display "flex"
                                    :flex-direction "column"
                                    :gap "2ex"
                                    :margin-left "14px"
                                    :padding-top "12px"
                                    :border-left "1px solid gray"
                                    :padding-bottom "2ex"}}

                           (properties-component schema editable? editing?)))))
             (c/handle-action
              (fn [node action]
                (if (is-a? discard-edit-action action)
                  (edit-tree/discard-edit node
                                          (discard-edit-action-predicate action)
                                          (discard-edit-action-index action))
                  (c/return :action action)))))

         (when editing?
           (set-properties schema))))))))

(defn edit-tree-component [schema types editable? force-editing?]
  (c/with-state-as etree
    (cond
      (edit-tree/literal-string? etree)
      (c/focus edit-tree/literal-string-value
               (ds/input {:disabled (when-not editable?
                                      "disabled")}))

      (edit-tree/literal-decimal? etree)
      (c/focus edit-tree/literal-decimal-value
               (ds/input {:type "decimal"
                          :disabled (when-not editable?
                                      "disabled")}))

      (edit-tree/literal-boolean? etree)
      (c/focus edit-tree/literal-boolean-value
               (ds/input {:type "checkbox"
                          :disabled (when-not editable?
                                      "disabled")}))

      (edit-tree/ref? etree)
      (dom/div
       {:style {:display "flex"
                :gap "1em"
                :align-items "center"}}
       (the-circle)
       (dom/b "REF")
       (edit-tree/ref-uri etree))

      (edit-tree/node? etree)
      (dom/div
       (c/focus edit-node-type
                (apply
                 ds/select
                 {:disabled (when-not editable? "disabled")}
                 (map (fn [type]
                        (forms/option {:value type} (schema/label-for-type schema type)))
                      types)))
       (node-component schema editable? force-editing?))
      )))

;; The editor handles rooted graphs with edits

(c/defn-item edit-trees-component [schema editable? force-editing?]
  (c/with-state-as etrees
    (apply
     dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :gap "2ex"}}
     (map-indexed (fn [idx etree]
                    (c/focus (lens/at-index idx)
                             (edit-tree-component schema [(edit-node-type etree)] editable? force-editing?)))
                  etrees))))

(c/defn-item edit-graph [schema editable? force-editing? graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (edit-trees-component schema editable? force-editing?)))

(c/defn-item readonly-graph [schema graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (edit-trees-component schema false false)))

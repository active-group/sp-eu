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
            [wisen.frontend.leaflet :as leaflet]
            [wisen.frontend.schema :as schema]
            [wisen.frontend.spinner :as spinner]
            [wisen.common.prefix :as prefix]))

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

(c/defn-item ^:private before-after [show? before-item after-item]
  (c/with-state-as [state before-or-after :local ::after]
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"}}

     (when show?
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
                          "After"))))

     (dom/div
      {:style (when show? {:border "1px solid gray"
                           :padding "1ex 1em"})}
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
                    [new-metree]

                    (edit-tree/maybe-changed? old-metree)
                    [(edit-tree/maybe-changed-result-value old-metree
                                                           (edit-tree/added-result-value new-metree))]))
                ;; else choose new metrees (TODO
                new-metrees)))
           old-eprops*
           new-eprops)))))))

(declare edit-tree-component)

(let [d 0.001]
  (defn- view-box-around [[lat long]]
    [[(- lat d) (+ lat d)]
     [(- long d) (+ long d)]]))

(declare day-of-week-component
         opening-hours-specification-component
         postal-address-component
         edit-node-is-postal-address-value?
         edit-node-is-opening-hours-specification-value?)

(c/defn-item ^:private component-for-predicate [predicate schema editable? editing?]
  (c/with-state-as etree
    (cond
      (= predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
      (c/with-state-as node
        (schema/label-for-type schema (edit-tree/edit-tree-result-tree node)))

      (= predicate "http://schema.org/name")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/input+focus {:disabled (when-not editable? "disabled")}))

      (= predicate "http://schema.org/description")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/textarea+focus {:style {:width "100%"
                                           :min-height "6em"}
                                   :disabled (when-not editable?
                                               "disabled")}))

      (= predicate "http://schema.org/keywords")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/input+focus {:disabled (when-not editable? "disabled")}))

      (= predicate "http://schema.org/byDay")
      (day-of-week-component schema editable? editing?)

      (and
       (= predicate "http://schema.org/openingHoursSpecification")
       (edit-tree/edit-node? etree)
       (= (tree/type-uri (edit-node-type etree)) "http://schema.org/OpeningHoursSpecification")
       (edit-node-is-opening-hours-specification-value? etree))
      (c/focus edit-tree/edit-node-properties-derived-uri
               (opening-hours-specification-component schema editable? editing?))

      (= predicate "http://schema.org/eventAttendanceMode")
      (c/focus edit-tree/tree-uri
               (ds/select
                {:disabled (when-not editable? "disabled")
                 :style {:padding "7px 8px"}}
                (forms/option {:value "http://schema.org/OfflineEventAttendanceMode"} "Offline")
                (forms/option {:value "http://schema.org/OnlineEventAttendanceMode"} "Online")
                (forms/option {:value "http://schema.org/MixedEventAttendanceMode"} "Mixed")))

      (= predicate "http://schema.org/url")
      (c/focus edit-tree/literal-string-value
               (ds/input {:type "url"
                          :placeholder "https://example.com"
                          :disabled (when-not editable? "disabled")}))

      (and
       (= predicate "http://schema.org/address")
       (edit-tree/edit-node? etree)
       (= (tree/type-uri (edit-node-type etree)) "http://schema.org/PostalAddress")
       (edit-node-is-postal-address-value? etree))
      (c/focus edit-tree/edit-node-properties-derived-uri
               (postal-address-component schema editable? editing?))

      (= predicate "https://wisen.active-group.de/target-group")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/select+focus
                {:disabled (when-not editable? "disabled")}
                (forms/option {:value "elderly"} "Elderly")
                (forms/option {:value "queer"} "Queer")
                (forms/option {:value "immigrants"} "Immigrants")))

      :else
      (dom/div
       (when editing?
         (c/focus (make-edit-tree-kind-lens schema predicate)
                  (apply
                   ds/select
                   {:disabled (when-not editable? "disabled")}
                   (map (fn [kind]
                          (forms/option {:value kind} (label-for-kind kind)))
                        (schema/kinds-for-predicate schema predicate)))))
       (edit-tree-component
        schema
        (schema/types-for-predicate schema predicate)
        editable?
        editing?)))))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (tree/type-uri (edit-tree/node-type node))))

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
   (ajax/POST "/describe" {:body prompt
                           :headers {:content-type "text/plain"}})
   :json-ld-string))

(defn- prompt-prefix [type]
  (str "The type is <" type ">."))

(defn- prepare-prompt [schema-type prompt]
  (str (prompt-prefix schema-type)
       " "
       prompt))

(c/defn-item ask-ai [schema close-action]
  (c/with-state-as [node local-state :local {:graphs nil ;; prompt -> graph
                                             :commit-prompt nil
                                             :prompt "Just come up with something!"}]
    (let [prompt (:prompt local-state)
          commit-prompt (:commit-prompt local-state)
          current-graph (get (:graphs local-state) prompt)
          loading? (and (some? commit-prompt)
                        (nil? current-graph))]

      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :overflow "auto"}}

       (modal/padded
        {:style {:display "flex"
                 :gap "1em"
                 :overflow "auto"}}

        (dom/div {:style {:padding "24px"
                          :color "#444"}}
                 (ds/lightbulb-icon "64"))

        (dom/div
         {:style {:flex 1
                  :display "flex"
                  :flex-direction "column"
                  :gap "2ex"}}

         (dom/h3 "Ask an AI to fill out this form")

         (dom/div
          {:style {:display "flex"
                   :flex-direction "column"
                   :gap "1ex"}}
          (dom/div (prompt-prefix (tree/node-uri
                                   (edit-tree/node-type node))))

          (c/focus (lens/>> lens/second :prompt)
                   (ds/textarea)))

         (when commit-prompt
           (c/focus (lens/>> lens/second :graphs (lens/member commit-prompt))
                    (util/load-json-ld-state (llm-query (prepare-prompt (tree/node-uri
                                                                         (edit-tree/node-type node))
                                                                        commit-prompt)))))

         (when current-graph
           (dom/div
            {:style {:padding "1ex 1em"
                     :background "#eee"
                     :border "1px solid gray"
                     :border-radius "8px"}}
            (dom/h3 "Result")
            (readonly-graph schema current-graph)))))

       (modal/toolbar

        (ds/button-secondary {:onClick #(c/return :action close-action)}
                             "Cancel")

        (when-not commit-prompt
          (c/focus lens/second
                   (ds/button-primary {:onClick (fn [ls]
                                                  (assoc ls :commit-prompt (:prompt ls)))}
                                      "Ask AI!")))

        (when loading?
          (spinner/main))

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

              (modal/modal-button (ds/lightbulb-icon "21") (partial ask-ai schema))))))

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
           {:style {:display "flex"
                    :flex-direction "column"
                    :gap "2ex"}}
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
                                       {:style {:min-width "15em"
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
                                                              "×"))


                                       (dom/div {:style {:width "100%"
                                                         :border-bottom "1px solid gray"
                                                         :position "absolute"
                                                         :top "14px"
                                                         :z-index "4"}}))

                                      (cond
                                        (edit-tree/deleted? marked-edit-tree)
                                        (c/focus edit-tree/deleted-original-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/added? marked-edit-tree)
                                        (c/focus edit-tree/added-result-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/maybe-changed? marked-edit-tree)
                                        ;; We need to wrap `maybe-changed` inside `before-after` so that
                                        ;; we do not lose focus in input fields when the user transitions
                                        ;; from `same?` to `changed?`
                                        (before-after
                                         (edit-tree/changed? marked-edit-tree)
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
        (dom/span {:style {:margin-right "1em"}
                   :id uri}
                  uri)

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

(letfn [(check-prop [pred matches? etree]
          (let [eprops (edit-tree/edit-node-properties etree)]
            (let [marked (lens/yank eprops (lens/>> (lens/member pred) lens/first))]
              (and (or (edit-tree/added? marked)
                       (edit-tree/maybe-changed? marked))
                   (matches? (edit-tree/marked-result-value marked))))))
        (check-derived-id [enode]
          (= (edit-tree/edit-node-uri enode)
             (edit-tree/derive-uri (edit-tree/edit-node-properties enode))))]

  (defn- edit-node-is-geo-coordinates-value? [etree]
    (and
     (check-derived-id etree)
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (edit-tree/edit-node? x)
                        (= "http://schema.org/GeoCoordinates"
                           (edit-tree/edit-node-uri x))))
                 etree)
     (check-prop "http://schema.org/latitude" edit-tree/literal-decimal? etree)
     (check-prop "http://schema.org/longitude" edit-tree/literal-decimal? etree)))

  (defn- edit-node-is-postal-address-value? [etree]
    (and
     (check-derived-id etree)
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (edit-tree/edit-node? x)
                        (= "http://schema.org/PostalAddress"
                           (edit-tree/edit-node-uri x))))
                 etree)
     (check-prop "http://schema.org/streetAddress" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/postalCode" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/addressLocality" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/addressCountry" edit-tree/literal-string? etree)))

  (defn- edit-node-is-opening-hours-specification-value? [etree]
    (and
     (check-derived-id etree)
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (edit-tree/edit-node? x)
                        (= "http://schema.org/OpeningHoursSpecification"
                           (edit-tree/edit-node-uri x))))
                 etree)
     (check-prop "http://schema.org/dayOfWeek" edit-tree/edit-node? etree)
     (check-prop "http://schema.org/opens" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/closes" edit-tree/literal-string? etree))))

(c/defn-item ^:private geo-coordinates-component [schema editable? force-editing?]
  (c/with-state-as eprops
    (let [latitude-lens (lens/>>
                         (lens/>>
                          (lens/member "http://schema.org/latitude")
                          lens/first
                          edit-tree/marked-result-value)
                         (lens/pattern [edit-tree/literal-decimal-value
                                        edit-tree/edit-tree-focused?]))
          latitude-value-lens (lens/>> latitude-lens lens/first)
          longitude-lens (lens/>>
                          (lens/>>
                           (lens/member "http://schema.org/longitude")
                           lens/first
                           edit-tree/marked-result-value)
                          (lens/pattern [edit-tree/literal-decimal-value
                                         edit-tree/edit-tree-focused?]))
          longitude-value-lens (lens/>> longitude-lens lens/first)
          lat (js/parseFloat (latitude-value-lens eprops))
          long (js/parseFloat (longitude-value-lens eprops))
          coords [lat long]]

      (dom/div
       {:style {:border "1px solid gray"
                :border-radius "3px"
                :display "flex"
                :flex-direction "column"}}
       (-> (c/isolate-state
            (view-box-around coords)
            (leaflet/main {:style {:height 200
                                   :min-width 400}}
                          [(leaflet/make-pin
                            "X"
                            "green"
                            coords)]))
           (c/handle-action (fn [eprops ac]
                              (if (is-a? leaflet/click-action ac)
                                (let [[lat lng] (leaflet/click-action-coordinates ac)]
                                  (-> eprops
                                      (latitude-lens lat)
                                      (longitude-lens lng)))
                                (c/return :action ac)))))
       (dom/div
        "Latitude:"
        (c/focus latitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")}))
        "Longitude:"
        (c/focus longitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")})))))))

(c/defn-item ^:private postal-address-component [schema editable? force-editing?]
  (c/with-state-as eprops
    (let [unpack-value (lens/>> lens/first
                                edit-tree/marked-result-value
                                edit-tree/literal-string-value)
          unpack-focus (lens/>> lens/first
                                edit-tree/marked-result-value
                                edit-tree/edit-tree-focused?)
          unpack (lens/pattern [unpack-value unpack-focus])

          street-address-lens (lens/>>
                               (lens/member "http://schema.org/streetAddress")
                               unpack)
          postal-code-lens (lens/>>
                            (lens/member "http://schema.org/postalCode")
                            unpack)
          address-locality-lens (lens/>>
                                 (lens/member "http://schema.org/addressLocality")
                                 unpack)
          address-country-lens (lens/>>
                                (lens/member "http://schema.org/addressCountry")
                                unpack)]
      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :gap "1ex"
                :border "1px solid gray"
                :border-radius "3px"
                :padding "1ex 1em"}}
       (dom/div
        (dom/label "Street address"
                   (dom/br)
                   (c/focus street-address-lens
                            (ds/input+focus
                             {:disabled (when-not editable? "disabled")}))))

       (dom/div
        (dom/label "Postal code"
                   (dom/br)
                   (c/focus postal-code-lens
                            (ds/input+focus
                             {:disabled (when-not editable? "disabled")}))))

       (dom/div
        (dom/label "Locality (Town)"
                   (dom/br)
                   (c/focus address-locality-lens
                            (ds/input+focus
                             {:disabled (when-not editable? "disabled")}))))

       (dom/div
        (dom/label "Country"
                   (dom/br)
                   (c/focus address-country-lens
                            (ds/input+focus
                             {:disabled (when-not editable? "disabled")}))))))))

(declare day-of-week-component)

(c/defn-item ^:private opening-hours-specification-component [schema editable? force-editing?]
  (c/with-state-as eprops
    (let [unpack (lens/>> lens/first
                          edit-tree/marked-result-value
                          edit-tree/literal-string-value)
          day-of-week-lens (lens/>>
                            (lens/member "http://schema.org/dayOfWeek")
                            lens/first
                            edit-tree/marked-result-value)
          opens-lens (lens/>>
                      (lens/member "http://schema.org/opens")
                      unpack)
          closes-lens (lens/>>
                       (lens/member "http://schema.org/closes")
                       unpack)]
      (dom/div
       {:style {:display "flex"
                :gap "1ex"
                :border "1px solid gray"
                :border-radius "3px"
                :padding "1ex 1em"}}
       (dom/div
        (dom/label "Day"
                   (dom/br)
                   (c/focus day-of-week-lens
                            (day-of-week-component schema editable? force-editing?))))

       (dom/div
        (dom/label "Opens"
                   (dom/br)
                   (c/focus opens-lens
                            (ds/input
                             {:type "time"
                              :disabled (when-not editable? "disabled")}))))

       (dom/div
        (dom/label "Closes"
                   (dom/br)
                   (c/focus closes-lens
                            (ds/input
                             {:type "time"
                              :disabled (when-not editable? "disabled")}))))))))

(c/defn-item ^:private day-of-week-component [schema editable? force-editing?]
  (c/focus (lens/pattern [edit-tree/tree-uri
                          edit-tree/edit-tree-focused?])
           (ds/select+focus
            {:disabled (when-not editable? "disabled")
             :style {:padding "7px 8px"}}
            (forms/option {:value "http://schema.org/Monday"} "Monday")
            (forms/option {:value "http://schema.org/Tuesday"} "Tuesday")
            (forms/option {:value "http://schema.org/Wednesday"} "Wednesday")
            (forms/option {:value "http://schema.org/Thursday"} "Thursday")
            (forms/option {:value "http://schema.org/Friday"} "Friday")
            (forms/option {:value "http://schema.org/Saturday"} "Saturday")
            (forms/option {:value "http://schema.org/Sunday"} "Sunday"))))

(c/defn-item ^:private node-component-for-type [type schema editable? force-editing?]
  (c/with-state-as enode
    (let [type-uri (tree/type-uri type)]
      (cond
        (and (= type-uri "http://schema.org/GeoCoordinates")
             (edit-node-is-geo-coordinates-value? enode))
        (c/focus edit-tree/edit-node-properties-derived-uri
                 (geo-coordinates-component schema editable? force-editing?))

        (and (= type-uri "http://schema.org/PostalAddress")
             (edit-node-is-postal-address-value? enode))
        (c/focus edit-tree/edit-node-properties-derived-uri
                 (postal-address-component schema editable? force-editing?))

        (and (= type-uri "http://schema.org/OpeningHoursSpecification")
             (edit-node-is-opening-hours-specification-value? enode))
        (c/focus edit-tree/edit-node-properties-derived-uri
                 (opening-hours-specification-component schema editable? force-editing?))

        :else
        (node-component schema editable? force-editing?)))))

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
      (let [uri (edit-tree/ref-uri etree)]
        (dom/div
         {:style {:display "flex"
                  :gap "1em"
                  :align-items "center"}}
         (the-circle)
         (dom/b "REF")
         (dom/a {:href (str "#" uri)} uri)))

      (edit-tree/node? etree)
      (dom/div
       (when force-editing?
         (c/focus edit-node-type
                  (apply
                   ds/select
                   {:disabled (when-not editable? "disabled")}
                   (map (fn [type]
                          (forms/option {:value type} (schema/label-for-type schema type)))
                        (or types
                            [(edit-node-type etree)])))))
       (node-component-for-type (edit-node-type etree) schema editable? force-editing?)))))

;; The editor handles rooted graphs with edits

(defn edit-trees-component

  ([schema editable? force-editing?]
   (edit-trees-component schema nil editable? force-editing?))

  ([schema types editable? force-editing?]
   (c/with-state-as etrees
     (apply
      dom/div
      {:style {:display "flex"
               :flex-direction "column"
               :gap "2ex"}}
      (map-indexed (fn [idx etree]
                     (c/focus (lens/at-index idx)
                              (edit-tree-component schema types editable? force-editing?)))
                   etrees)))))

(c/defn-item edit-graph [schema editable? force-editing? graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (edit-trees-component schema nil editable? force-editing?)))

(c/defn-item readonly-graph [schema graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (edit-trees-component schema nil false false)))

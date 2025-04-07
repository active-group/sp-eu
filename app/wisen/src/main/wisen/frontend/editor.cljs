(ns wisen.frontend.editor
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            #_[wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree-2 :as edit-tree]
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

(defn- color-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    "red"

    (edit-tree/added? marked)
    "green"

    (edit-tree/same? marked)
    "gray"

    (edit-tree/changed? marked)
    "orange"))

(defn- style-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    {:border "1px solid red"
     :color "red"
     :text-decoration "line-through"}

    (edit-tree/added? marked)
    {:border "1px solid green"
     :color "green"}

    (edit-tree/same? marked)
    {:border "1px solid gray"}

    (edit-tree/changed? marked)
    {:border "1px solid orange"
     :color "orange"}))

(def-record delete-property-action [])

(defn pprint [x]
  (dom/pre
   (with-out-str
     (cljs.pprint/pprint x))))

(declare edit-tree-component)

(def-record delete-property [delete-property-property])

(defn component-for-predicate [predicate schema editable? editing?]
  (case predicate
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    (c/with-state-as node
      (schema/label-for-type schema (edit-tree/type-uri node)))

    "http://schema.org/description"
    (c/focus edit-tree/literal-string-value
             (if editing?
               (ds/textarea {:style {:width "100%"
                                     :min-height "6em"}})
               (c/dynamic dom/div)))

    "http://schema.org/dayOfWeek"
    (c/focus edit-tree/node-uri (ds/select
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
              (forms/option {:value "elderly"} "Elderly")
              (forms/option {:value "queer"} "Queer")
              (forms/option {:value "immigrants"} "Immigrants")))

    (edit-tree-component
     schema
     (schema/sorts-for-predicate schema predicate)
     editable?
     editing?)))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (edit-tree/type-uri (edit-tree/node-type node))))

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

(c/defn-item graph-resolver
  "Take an ajax request that yields json-ld as response. Turn that
  response into an rdf graph and set that as state."
  [request]
  (c/with-state-as [graph responses :local {}]
    (when (nil? graph)
      (c/fragment
       (c/focus (lens/>> lens/second (lens/member request))
                (ajax/fetch request))

       (when-let [current-response (get responses request)]
         (when (ajax/response-ok? current-response)
           (c/focus lens/first
                    (promise/call-with-promise-result
                     (rdf/json-ld-string->graph-promise (ajax/response-value current-response))
                     (comp c/once constantly)))))))))

(c/defn-item osm-importer [schema osm-uri]
  (c/with-state-as graph
    (c/fragment

     (when (some? osm-uri)
       (graph-resolver (osm/osm-lookup-request osm-uri)))

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
                   (graph-resolver (llm-query (prepare-prompt (edit-tree/type-uri
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
     (edit-tree/node-uri node)
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
       (-> (refresh-node (edit-tree/node-uri node))
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

(c/defn-item property-object-component [schema predicate editable? force-editing?]
  (c/with-state-as marked-edit-trees
    (apply dom/div
           (map-indexed (fn [idx _]
                          (c/focus (lens/at-index idx)
                                   (c/with-state-as marked-edit-tree
                                     (dom/div
                                      {:style {:flex 1
                                               :display "flex"}}

                                      (dom/div
                                       {:style {:min-width "10em"
                                                :position "relative"
                                                :display "flex"
                                                :gap "1em"
                                                :align-items "flex-start"}}

                                       (dom/div
                                        {:style (merge {:background "white"
                                                        :display "inline-flex"
                                                        :border (str "1px solid gray")
                                                        :margin-left "10px"
                                                        :padding "4px 12px"
                                                        :position "relative"
                                                        :z-index "5"}
                                                       (style-for-marked marked-edit-tree))}
                                        (schema/label-for-predicate schema predicate))

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
                                        (c/focus edit-tree/deleted-result-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/added? marked-edit-tree)
                                        (c/focus edit-tree/added-result-value
                                                 (component-for-predicate predicate schema editable? force-editing?))

                                        (edit-tree/maybe-changed? marked-edit-tree)
                                        (dom/div
                                         (when (edit-tree/changed? marked-edit-tree)
                                           (c/focus edit-tree/maybe-changed-original-value
                                                    (dom/div
                                                     {:style {:text-decoration "line-through"
                                                              :color "red"}}
                                                     (component-for-predicate predicate schema false false))))
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

     (map (fn [predicate]
            (c/focus (lens/member predicate)
                     (property-object-component schema predicate editable? force-editing?)))

          (sort schemaorg/compare-predicate (keys properties))))))

(defn- node-component [schema editable? force-editing?]
  (c/with-state-as [node local-state :local {:editing? force-editing?
                                             :open? true}]

    (let [editing? (:editing? local-state)
          uri (edit-tree/node-uri node)]

      (details/details
       {:id uri}

       (lens/>> lens/second :open?)

       (details/summary
        {:style {:color "#555"
                 :cursor "pointer"
                 :display "flex"
                 :align-items "center"
                 :gap "1em"}}
        (the-circle)
        (dom/span {:style {:margin-right "1em"}} uri)

        (when editing?
                (c/fragment
                 (c/focus lens/first
                          (modal/modal-button "Set reference" set-reference))
                 " | "))

        (c/focus lens/first (refresh-button))

        " | "

        (when editable?
          (c/focus (lens/>> lens/second :editing?)
                   (ds/button-primary {:onClick not}
                                      (if editing? "Done" "Edit")))))

       (c/focus
        lens/first
        (dom/div

         (dom/div
          {:style {:display "flex"
                   :flex-direction "column"
                   :gap "2ex"
                   :margin-left "14px"
                   :padding-top "12px"
                   :border-left "1px solid gray"
                   :padding-bottom "2ex"}}

          (c/focus edit-tree/edit-node-properties
                   (properties-component schema editable? force-editing?)))

         (when editing?
           (set-properties schema))))))))

(defn edit-tree-component [schema sorts editable? force-editing?]
  (c/with-state-as etree
    (cond
      (edit-tree/literal-string? etree)
      (c/focus edit-tree/literal-string-value
               (if force-editing?
                 (ds/input)
                 (c/dynamic str)))

      (edit-tree/literal-decimal? etree)
      (c/focus edit-tree/literal-decimal-value
               (if force-editing?
                 (ds/input {:type "decimal"})
                 (c/dynamic str)))

      (edit-tree/literal-boolean? etree)
      (c/focus edit-tree/literal-boolean-value
               (if force-editing?
                 (ds/input {:type "checkbox"})
                 (c/dynamic str)))

      (edit-tree/ref? etree)
      (dom/div "REF: " (edit-tree/ref-uri etree))

      (edit-tree/node? etree)
      (node-component schema editable? force-editing?)
      )))

;; The editor handles rooted graphs with edits

(c/defn-item edit-trees-component [schema editable? force-editing?]
  (c/with-state-as etrees
    (apply
     dom/div
     (map-indexed (fn [idx _]
                    (c/focus (lens/at-index idx)
                             (edit-tree-component schema [] editable? force-editing?)))
                  etrees))))

(defn display-edits [edits]
  "TODO")

(c/defn-item edit-graph [schema editable? force-editing? graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (dom/div
                    (edit-trees-component schema editable? force-editing?)
                    (c/with-state-as etrees
                      (display-edits (apply concat (map edit-tree/edit-tree-changes etrees)))))))

(c/defn-item readonly-graph [schema graph]
  (c/isolate-state (edit-tree/graph->edit-trees graph)
                   (edit-trees-component schema false false)))

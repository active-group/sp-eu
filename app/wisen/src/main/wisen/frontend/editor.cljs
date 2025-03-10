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
            [wisen.frontend.change :as change]
            [wisen.common.change-api :as change-api]
            [wisen.frontend.default :as default]
            [wisen.frontend.osm :as osm]
            [active.data.record :as record :refer-macros [def-record]]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.schemaorg :as schemaorg]
            [wisen.frontend.schema :as schema]))

;; [ ] Fix links for confluences
;; [x] Load all properties
;; [x] Focus
;; [ ] Patterns for special GUIs
;; [x] Style

(def-record focus-query-action
  [focus-query-action-query])

(def-record expand-by-query-action
  [expand-by-query-action-query])

(defn- load-more-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?x1 . }
          WHERE { <" uri "> ?p ?x1 . }"))

(defn- load-more-button [uri]
  (dom/button
   {:style {}
    :onclick (fn [_]
               (c/return :action
                         (expand-by-query-action expand-by-query-action-query
                                                 (load-more-query uri))))}
   (if ::TODO "v" ">")))

(declare tree-component)

(def-record delete-property [delete-property-property :- tree/property])

(defn component-for-predicate [predicate schema editable? editing? can-focus? can-expand?]
  (case predicate
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    (c/dynamic (partial schema/label-for-sort schema))

    "http://schema.org/description"
    (c/focus tree/literal-string-value
             (if editing?
               (ds/textarea {:style {:width "100%"
                                     :min-height "6em"}})
               (c/dynamic dom/div)))

    "http://schema.org/dayOfWeek"
    (c/focus tree/node-uri (ds/select
                            (forms/option {:value "http://schema.org/Monday"} "Monday")
                            (forms/option {:value "http://schema.org/Tuesday"} "Tuesday")
                            (forms/option {:value "http://schema.org/Wednesday"} "Wednesday")
                            (forms/option {:value "http://schema.org/Thursday"} "Thursday")
                            (forms/option {:value "http://schema.org/Friday"} "Friday")
                            (forms/option {:value "http://schema.org/Saturday"} "Saturday")
                            (forms/option {:value "http://schema.org/Sunday"} "Sunday")
                            ))

    (dom/div {:style {:margin-left "0em"
                      :display "flex"}}
             (tree-component
              schema
              (schema/sorts-for-predicate schema predicate)
              editable?
              editing?
              can-focus?
              can-expand?))))

(c/defn-item property-component [schema editable? editing? can-focus? can-expand?]
  (c/with-state-as property
    (let [predicate (tree/property-predicate property)]
      (dom/div
       {:style {:padding "1ex 0"
                :display "flex"
                :align-items "flex-start"
                :gap "1em"}}
       (dom/div
        {:style {:flex 1 :display "flex" :gap "1em" :align-items "baseline"}}
        (dom/div
         {:style {:min-width "10em"}}
         (dom/strong
          (schema/label-for-predicate schema predicate))
         (when editing?
           (ds/button-secondary {:onClick (constantly
                                           (c/return :action ::delete))
                                 :style {:font-size "25px"
                                         :font-weight "normal"
                                         :cursor "pointer"
                                         :margin-left "6px"}}
                                "×")))
        (c/focus tree/property-object
                 (component-for-predicate predicate schema editable? editing? can-focus? can-expand?)))
       ))))

(defn- focus-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?o . }
          WHERE { <" uri "> ?p ?o . }"))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (tree/type-uri (tree/node-type node))))

(c/defn-item add-property-button [schema predicates]
  (c/with-state-as [resource predicate :local schemaorg/default-predicate]
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
          (c/return :state [(tree/node-assoc node
                                             predicate
                                             (default/default-tree-for-sort
                                              (first (schema/sorts-for-predicate schema predicate))))
                            predicate]))}
       "Add property")))))

(defn- remove-index
  "remove elem in coll"
  [pos coll]
  (let [v (vec coll)]
    (into (subvec v 0 pos) (subvec v (inc pos)))))

;; OSM

(defn- pr-osm-uri [uri]
  (dom/a {:href uri}
         "View on OpenStreetMap"))

(declare readonly)

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

     (when graph (readonly schema graph)))))

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
                     (let [place-node (first (tree/graph->trees (:graph local-state)))]
                       (assert (tree/node? place-node))
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
                   (graph-resolver (llm-query (prepare-prompt (tree/node-uri
                                                               (tree/node-type node))
                                                              commit-prompt)))))

        (when current-graph
          #_(pr-str current-graph)
          (readonly schema current-graph)))

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
                       (let [ai-node (first (tree/graph->trees current-graph))]
                         (assert (tree/node? ai-node))
                         (c/return :state [#_(tree/merge node ai-node)
                                           ai-node
                                           {}]
                                   :action close-action)))}
           "Use these properties")))))))

;; ---

(c/defn-item modal-button [title item-f]
  (c/with-state-as [state show? :local false]
    (c/fragment

     (c/focus lens/second
              (ds/button-primary {:onClick (constantly true)} title))

     (when show?
       (-> (modal/main
            ::close-action
            (c/focus lens/first (item-f ::close-action)))

           (c/handle-action (fn [[state local-state] ac]
                              (if (= ::close-action ac)
                                (c/return :state [state false])
                                (c/return :action ac)))))))))

(defn- node-component-header [schema]
  (c/with-state-as node
    (dom/div {:style {:display "flex"
                      :padding "1.5ex 16px"
                      :background "rgba(218, 224, 235, 0.7)"
                      :gap "2em"
                      :align-items "center"
                      :border-bottom "1px solid gray"
                      :justify-content "space-between"}}

             (add-property-button schema (schema/predicates-for-type schema (tree/node-type node)))

             (dom/div
              {:style {:display "flex"
                       :gap "1em"}}
              (when (node-organization? node)
                (if-let [osm-uri (osm/node-osm-uri node)]

                  (dom/div
                   {:style {:display "flex"
                            :gap "1em"}}
                   (pr-osm-uri osm-uri)
                   (modal-button "Update" #(link-organization-with-osm-button schema osm-uri %)))
                  (modal-button "Link with OpenStreetMap" #(link-organization-with-osm-button schema nil %))))

              (modal-button "Ask AI" (partial ask-ai schema))))))

(defn- node-component [schema editable? force-editing? can-focus? can-expand?]
  (c/with-state-as [node editing? :local force-editing?]
    (let [uri (tree/node-uri node)]
      (dom/details
       {:id uri
        :style {:border "1px solid gray"
                :box-shadow "0 1px 0px rgba(0,0,0,0.5)"
                :background "rgba(255,255,255,0.5)"
                :border-radius "0 4px 4px 4px"}}

       (dom/summary
        {:style {:color "#555"
                 :border-bottom "1px solid gray"
                 :padding "8px 16px"
                 :cursor "pointer"}}
        (dom/span {:style {:margin-right "1em"}} uri)
        (when editable?
          (c/focus lens/second
                   (ds/button-primary {:onClick not} "Edit"))))

       (c/focus
        lens/first
        (dom/div

         (when editing?
           (node-component-header schema))

         (let [props (tree/node-properties node)]
           (when-not (empty? props)

             (c/focus tree/node-properties
                      (apply
                       dom/div
                       {:style {:display "flex"
                                :flex-direction "column"
                                :padding "0 1em"}}

                       (->> props
                            (map-indexed (fn [idx property]
                                           [(tree/property-predicate property)
                                            (c/handle-action
                                             (c/focus (lens/at-index idx)
                                                      (property-component schema editable? editing? can-focus? can-expand?))
                                             (fn [props ac]
                                               (if (= ::delete ac)
                                                 (c/return :state (remove-index idx props))
                                                 (c/return :action ac)))
                                             )]))
                            (remove (comp schemaorg/hide-predicate first))
                            (sort-by first schemaorg/compare-predicate)
                            (map second)
                            (interpose (dom/hr {:style {:width "100%"}})))))))))))))

(defn tree-component [schema sorts editable? force-editing? can-focus? can-expand?]
  (c/with-state-as tree
    (if (tree/primitive? tree)
      (dom/div
       {:style {:display "flex"
                :flex-direction "row"
                :align-items "baseline"
                :border (when force-editing? "1px solid #888")
                :border-radius "3px"}}
       (c/focus default/tree-sort
                (if force-editing?
                  (apply ds/select
                         {:style {:border 0}}
                         (map (fn [sort]
                                (forms/option {:value sort} (schema/label-for-sort schema sort)))
                              sorts))
                  (when-not (tree/primitive? tree)
                    (dom/i
                     (c/dynamic (partial schema/label-for-sort schema))))))
       (let [primitive-style {:border-radius "0 3px 3px 0"
                              :border-width "0 0 0 1px"
                              :border-color "#888"
                              }]
         (cond
           (tree/literal-string? tree)
           (c/focus tree/literal-string-value
                    (if force-editing?
                      (ds/input {:style primitive-style})
                      (c/dynamic str)))

           (tree/literal-decimal? tree)
           (c/focus tree/literal-decimal-value
                    (if force-editing?
                      (ds/input {:type "decimal"
                                 :style primitive-style})
                      (c/dynamic str)))

           (tree/literal-boolean? tree)
           (c/focus tree/literal-boolean-value
                    (if force-editing?
                      (ds/input {:type "checkbox"
                                 :style primitive-style})
                      (c/dynamic str)))

           (tree/ref? tree)
           (dom/div "REF: " (tree/ref-uri tree)))))

      ;; else node
      (dom/div
       {:style {:display "flex"
                :align-items "baseline"
                :flex-direction "column"}}
       (c/focus default/tree-sort
                (if force-editing?
                  (apply ds/select
                         {:style {:border-width "1px 1px 0 1px"
                                  :border-radius "3px 3px 0 0"
                                  }}
                         (map (fn [sort]
                                (forms/option {:value sort}
                                              (schema/label-for-sort schema sort)))
                              sorts))
                  (dom/i
                   (c/dynamic (partial schema/label-for-sort schema)))))

       (node-component schema editable? force-editing? can-focus? can-expand?)))))

(c/defn-item commit-changes [changes]
  (c/isolate-state
   nil
   (c/fragment
    (c/dynamic pr-str)
    (ajax/fetch (change/commit-changes-request changes)))))

(c/defn-item main* [schema editable? force-editing? can-focus? can-expand?]
  (c/with-state-as trees
    (c/isolate-state
     ;; working trees
     trees
     (c/with-state-as working-trees
       (dom/div
        (when editable?
          (change/changes-component
           schema
           (change/delta-trees trees working-trees)))

        (when editable?
          (c/isolate-state false
                           (c/with-state-as commit?
                             (c/fragment
                              (pr-str commit?)
                              (when commit?
                                (commit-changes (change/delta-trees trees working-trees)))
                              (dom/button {:onclick (constantly true)} "Commit changes")))))

        (dom/hr)
        (apply
         dom/div
         (map-indexed (fn [idx _]
                        (c/focus (lens/at-index idx)
                                 (tree-component schema [] editable? force-editing? can-focus? can-expand?)))
                      trees)))))))

(defn main [schema editable? force-editing? make-focus-query-action make-expand-by-query-action]
  (let [can-focus? (some? make-focus-query-action)
        can-expand? (some? make-expand-by-query-action)]
    (c/handle-action
     (c/focus tree/graph<->trees (main* schema editable? force-editing? can-focus? can-expand?))
     (fn [_ ac]
       (c/return :action
                 (cond
                   (record/is-a? focus-query-action ac)
                   (make-focus-query-action (focus-query-action-query ac))

                   (record/is-a? expand-by-query-action ac)
                   (make-expand-by-query-action (expand-by-query-action-query ac))

                   :else
                   ac))))))

(defn readonly [schema graph & [make-focus-query-action make-expand-by-query-action]]
  (c/isolate-state graph (main schema false false make-focus-query-action make-expand-by-query-action)))

(defn readwrite [schema graph & [make-focus-query-action make-expand-by-query-action]]
  (c/isolate-state graph (main schema true false make-focus-query-action make-expand-by-query-action)))

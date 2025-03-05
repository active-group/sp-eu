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
    nil

    "http://schema.org/name"
    (c/focus tree/literal-string-value
             (if editing?
               (forms/input {:style {:font-size "2em"
                                     :width "100%"}})
               (c/dynamic str)
               ))

    "http://schema.org/description"
    (c/focus tree/literal-string-value
             (if editing?
               (forms/textarea {:style {:width "100%"
                                        :min-height "6em"}})
               (c/dynamic dom/div)))

    "http://schema.org/dayOfWeek"
    (c/focus tree/node-uri (forms/select
                            (forms/option {:value "http://schema.org/Monday"} "Monday")
                            (forms/option {:value "http://schema.org/Tuesday"} "Tuesday")
                            (forms/option {:value "http://schema.org/Wednesday"} "Wednesday")
                            (forms/option {:value "http://schema.org/Thursday"} "Thursday")
                            (forms/option {:value "http://schema.org/Friday"} "Friday")
                            (forms/option {:value "http://schema.org/Saturday"} "Saturday")
                            (forms/option {:value "http://schema.org/Sunday"} "Sunday")
                            ))

    (dom/div {:style {:margin-left "0em"
                      :margin-top "1ex"
                      :display "flex"}}
             (tree-component
              schema
              (schemaorg/tree-sorts-for-predicate predicate)
              editable?
              editing?
              can-focus?
              can-expand?))))

(c/defn-item property-component [schema editable? editing? can-focus? can-expand?]
  (c/with-state-as property
    (let [predicate (tree/property-predicate property)]
      (when-let [value-item (component-for-predicate predicate schema editable? editing? can-focus? can-expand?)]
        (dom/div
         {:style {:display "flex"}}
         (dom/div
          {:style {:flex 1}}
          (dom/div (dom/strong
                    (schema/label-for-predicate schema predicate)))
          (c/focus tree/property-object value-item))
         (when editing?
           (dom/button {:onClick (constantly
                                  (c/return :action ::delete))}
                       "Delete")))))))

(defn- focus-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?o . }
          WHERE { <" uri "> ?p ?o . }"))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (tree/node-uri (tree/node-type node))))

(c/defn-item add-property-button [schema predicates]
  (c/with-state-as [resource predicate :local schemaorg/default-predicate]
    (dom/div
     (c/focus lens/second
              (apply
               forms/select
               (map (fn [pred]
                      (forms/option {:value pred} (schema/label-for-predicate schema pred)))
                    predicates)))

     (dom/button {:onClick
                  (fn [[node predicate] _]
                    (c/return :state [(tree/node-assoc node
                                                       predicate
                                                       (default/default-tree-for-predicate predicate))
                                      predicate]))}
                 "Add property"))))

(defn- remove-index
  "remove elem in coll"
  [pos coll]
  (let [v (vec coll)]
    (into (subvec v 0 pos) (subvec v (inc pos)))))

(defn- pr-osm-uri [uri]
  (dom/a {:href uri}
         "View on OpenStreetMap"))

(defn- enter-osm-uri [initial-osm-uri]
  (c/with-state-as [osm-uri osm-uri-local :local (or initial-osm-uri "")]
    (dom/div
     (c/focus lens/second
              (forms/input
               {:type "url"
                :placeholder "https://www.openstreetmap.org/..."}))
     (dom/button
      {:onClick (fn [[_ osm-uri-local]]
                  [osm-uri-local osm-uri-local])}
      "Go"))))

(declare readonly)

(c/defn-item osm-importer []
  (c/with-state-as [state ;; {:graphs :osm-uri}
                    responses :local {} ;; osm-uri -> response
                    ]
    (let [osm-uri (:osm-uri state)
          response (get responses osm-uri)
          graph (get (:graphs state) osm-uri)]
      (dom/div
       (ds/padded-2
        {:style {:overflow "auto"}}
        (dom/h2 "OSM importer")

        (c/focus (lens/>> lens/first :osm-uri)
                 (enter-osm-uri (:osm-uri state)))

        (when (and (some? osm-uri)
                   (nil? graph))
          (c/focus (lens/>> lens/second (lens/member osm-uri))
                   (ajax/fetch (osm/osm-lookup-request osm-uri))))

        (let [response (get responses osm-uri)]
          (when (and (ajax/response? response)
                     (ajax/response-ok? response)
                     (nil? graph))
            (c/focus (lens/>> lens/first :graphs (lens/member osm-uri))
                     (promise/call-with-promise-result
                      (rdf/json-ld-string->graph-promise (ajax/response-value response))
                      (comp c/once constantly)))))

        (when graph (readonly graph)))))))

(c/defn-item link-organization-with-osm-button [button-title & [osm-uri]]
  (c/with-state-as [node local-state :local {:show? false
                                             :graphs nil ;; osm-uri -> graph
                                             :osm-uri osm-uri}]
    (c/fragment
     (c/focus (lens/>> lens/second :show?)
              (dom/button {:onClick (constantly true)}
                          button-title))
     (when (:show? local-state)
       (-> (modal/main
            {:style {:border "1px solid blue"}}
            ::close-action
            (dom/div
             (c/focus (lens/>> lens/second)
                      (osm-importer))

             (c/focus (lens/>> lens/second :show?)
                      (dom/button {:onClick (constantly false)}
                                  "Cancel"))

             (ds/button-primary
              {:onClick (fn [[node local-state]]
                          (let [place-node (first (tree/graph->trees (get (:graphs local-state)
                                                                          (:osm-uri local-state))))]
                            (assert (tree/node? place-node))
                            [(osm/organization-do-link-osm node (:osm-uri local-state) place-node)
                             (-> local-state
                                 (assoc :show? false)
                                 (dissoc :graphs)
                                 (dissoc :osm-uri))]))}
              "Add properties as 'location'")))
           (c/handle-action (fn [[node local-state] ac]
                              (if (= ::close-action ac)
                                (c/return :state [node (assoc local-state :show? false)])
                                (c/return :action ac)))))))))

(defn- node-component [schema editable? force-editing? can-focus? can-expand?]
  (c/with-state-as [node editing? :local force-editing?]
    (let [uri (tree/node-uri node)]
      (ds/card
       {:id uri}

       ;; header
       (dom/div {:style {:display "flex"
                         :justify-content "flex-start"
                         :align-items "center"
                         :background "rgba(0,0,0,0.1)"}}

                (when can-expand?
                  (ds/padded-1
                   (load-more-button uri)))

                (ds/padded-1
                 {:style {:color "#555"
                          :font-size "12px"}}
                 uri)

                (when editable?
                  (c/focus lens/second
                           (dom/button {:onClick not} "Edit mode")))

                (when can-focus?
                  (ds/padded-1
                   (dom/button {:onClick
                                (fn [_]
                                  (c/return :action
                                            (focus-query-action focus-query-action-query
                                                                (focus-query uri))))}
                               "Focus"))))

       (c/focus
        lens/first

        (c/fragment

         ;; OSM
         (when (and editing?
                    (node-organization? node))
           (ds/with-card-padding
             (if-let [osm-uri (osm/node-osm-uri node)]

               (dom/div
                {:style {:display "flex"
                         :gap "1em"}}
                (pr-osm-uri osm-uri)
                (link-organization-with-osm-button "Update" osm-uri))
               (link-organization-with-osm-button "Link with OpenStreetMap"))))

         (let [props (tree/node-properties node)]
           (when-not (empty? props)

             (c/focus tree/node-properties
                      (ds/with-card-padding
                        (apply
                         dom/div
                         {:style {:display "flex"
                                  :flex-direction "column"
                                  :gap "2ex"}}

                         (map second
                              (sort-by
                               first
                               schemaorg/compare-predicate
                               (map-indexed (fn [idx prop]
                                              [(tree/property-predicate prop)
                                               (c/handle-action
                                                (c/focus (lens/at-index idx)
                                                         (property-component schema editable? editing? can-focus? can-expand?))
                                                (fn [props ac]
                                                  (if (= ::delete ac)
                                                    (c/return :state (remove-index idx props))
                                                    (c/return :action ac)))
                                                )])
                                            props))))))))

         (when editing?
           (add-property-button schema (schemaorg/predicates-for-type (tree/node-type node))))))
       ))))

(defn tree-component [schema sorts editable? force-editing? can-focus? can-expand?]
  (c/with-state-as tree
    (dom/div
     (c/focus default/tree-sort
              (if force-editing?
                (apply forms/select (map (fn [sort]
                                           (forms/option {:value sort} (schema/label-for-sort schema sort)))
                                         sorts))
                (c/dynamic (partial schema/label-for-sort schema))))
     (cond
       (tree/node? tree)
       (node-component schema editable? force-editing? can-focus? can-expand?)

       (tree/literal-string? tree)
       (c/focus tree/literal-string-value
                (if force-editing?
                  (forms/input)
                  (c/dynamic str)))

       (tree/literal-decimal? tree)
       (c/focus tree/literal-decimal-value
                (if force-editing?
                  (forms/input {:type "decimal"})
                  (c/dynamic str)))

       (tree/ref? tree)
       (dom/div "REF: " (tree/ref-uri tree))))))

(defn commit-changes-request [changes]
  (ajax/PUT "/api/triples"
            {:body (pr-str {:changes
                            (map change-api/change->edn changes)})
             :headers {:content-type "application/edn"}}))

(c/defn-item commit-changes [changes]
  (c/isolate-state
   nil
   (c/fragment
    (c/dynamic pr-str)
    (ajax/fetch (commit-changes-request
                 (map change/change->api changes))))))

(c/defn-item main* [schema editable? force-editing? can-focus? can-expand?]
  (c/with-state-as trees
    (c/isolate-state
     ;; working trees
     trees
     (c/with-state-as working-trees
       (dom/div
        (when editable?
          (pr-str (change/delta-trees trees working-trees)))

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
                                 (tree-component schema schemaorg/tree-sorts editable? force-editing? can-focus? can-expand?)))
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

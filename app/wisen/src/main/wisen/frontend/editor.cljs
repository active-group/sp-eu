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
            [wisen.frontend.modal :as modal]))

;; [ ] Fix links for confluences
;; [x] Load all properties
;; [x] Focus
;; [ ] Patterns for special GUIs
;; [x] Style

(def-record focus-query-action
  [focus-query-action-query])

(def-record expand-by-query-action
  [expand-by-query-action-query])

(defn pr-type [t]
  (case t
    "http://schema.org/GeoCoordinates"
    "Geo coordinates"

    "http://schema.org/GeoCircle"
    "Geo circle"

    "http://schema.org/Organization"
    "Organization"

    "http://schema.org/PostalAddress"
    "Address"

    t
    ))

(defn pr-predicate [p]
  (case p
    "https://wisen.active-group.de/osm-uri"
    "OpenStreetMap URI"

    "http://schema.org/name"
    "Name"

    "http://schema.org/email"
    "E-Mail"

    "http://schema.org/url"
    "Website"

    "http://schema.org/geo"
    "The geo coordinates of the place"

    "http://schema.org/description"
    "Description"

    "http://schema.org/keywords"
    "Keywords"

    "http://schema.org/areaServed"
    "Area served"

    "http://schema.org/latitude"
    "Latitude"

    "http://schema.org/longitude"
    "Longitude"

    "http://schema.org/location"
    "Location"

    "http://schema.org/addressCountry"
    "Country (e.g. 'de' for Germany)"

    "http://schema.org/addressLocality"
    "Locality or town (e.g. 'Berlin')"

    "http://schema.org/postalCode"
    "Postal code"

    "http://schema.org/streetAddress"
    "Street Address"

    "http://schema.org/sameAs"
    "Same resource on other site (Google Maps, OpenStreetMap, ...)"

    p))

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

(defn component-for-predicate [predicate editable? editing? can-focus? can-expand?]
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

    (dom/div {:style {:margin-left "0em"
                      :margin-top "1ex"
                      :display "flex"}}
             (tree-component editable? editing? can-focus? can-expand?))))

(c/defn-item property-component [editable? editing? can-focus? can-expand?]
  (c/with-state-as property
    (let [predicate (tree/property-predicate property)]
      (when-let [value-item (component-for-predicate predicate editable? editing? can-focus? can-expand?)]
        (dom/div
         {:style {:display "flex"}}
         (dom/div
          {:style {:flex 1}}
          (dom/div (dom/strong
                    (pr-predicate predicate)))
          (c/focus tree/property-object value-item))
         (when editing?
           (dom/button {:onClick (constantly
                                  (c/return :action ::delete))}
                       "Delete")))))))

(defn- focus-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?o . }
          WHERE { <" uri "> ?p ?o . }"))

(defn- node-type [node]
  (when-let [obj ((tree/node-object-for-predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type") node)]
    (when (tree/node? obj)
      (tree/node-uri obj))))

(defn- node-organization? [node]
  (= "http://schema.org/Organization"
     (node-type node)))

(def predicate-priority
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/keywords"
   "http://schema.org/location"
   "http://schema.org/sameAs"

   "http://schema.org/streetAddress"
   "http://schema.org/postalCode"
   "http://schema.org/addressLocality"
   "http://schema.org/addressCountry"

   ])

(defn- index-of [s v]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (= v (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn- compare-predicate [p1 p2]
  (let [i1 (index-of predicate-priority p1)
        i2 (index-of predicate-priority p2)]
    (if i1
      (if i2
        (compare i1 i2)
        -1)
      (if i2
        1
        (compare p1 p2)))))

(def predicates
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/url"
   #_"http://schema.org/areaServed"
   "http://schema.org/location"
   "http://schema.org/sameAs"
   #_"http://schema.org/geo"
   ])

(def predicate-options
  (map (fn [pred]
         (forms/option
          {:value pred}
          (pr-predicate pred)))
       predicates
       ))

(c/defn-item add-property-button []
  (c/with-state-as [resource predicate :local "http://schema.org/name"]
    (dom/div
     (c/focus lens/second
              (apply
               forms/select
               predicate-options))

     (dom/button {:onClick
                  (fn [[node predicate] _]
                    (c/return :state [(tree/node-assoc node
                                                       predicate
                                                       (default/default-object-for-predicate predicate))
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

(defn- enter-osm-uri []
  (c/with-state-as [osm-uri osm-uri-local :local ""]
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
  (c/with-state-as [state ;; {:graph :osm-uri}
                    response :local nil]
    (let [graph (:graph state)
          osm-uri (:osm-uri state)]
      (dom/div
       (ds/padded-2
        {:style {:overflow "auto"}}
        (dom/h2 "OSM importer")

        (c/focus (lens/>> lens/first :osm-uri)
                 (enter-osm-uri))

        (when (and (some? osm-uri)
                   (nil? graph))
          (c/focus (lens/>> lens/second)
                   (ajax/fetch (osm/osm-lookup-request osm-uri))))

        (when (and (ajax/response? response)
                   (ajax/response-ok? response)
                   (nil? graph))
          (c/focus (lens/>> lens/first :graph)
                   (promise/call-with-promise-result
                    (rdf/json-ld-string->graph-promise (ajax/response-value response))
                    (comp c/once constantly))))

        (when graph (readonly graph)))))))

(c/defn-item link-organization-with-osm-button []
  (c/with-state-as [node local-state :local {:show? false
                                             :graph nil
                                             :osm-uri nil}]
    (c/fragment
     (c/focus (lens/>> lens/second :show?)
              (dom/button {:onClick (constantly true)}
                          "Link with OpenStreetMap"))
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
                          (let [place-node (first (tree/graph->trees (:graph local-state)))]
                            (assert (tree/node? place-node))
                            [(osm/organization-do-link-osm node (:osm-uri local-state) place-node)
                             (-> local-state
                                 (assoc :show? false)
                                 (dissoc :graph)
                                 (dissoc :osm-uri))]))}
              "Add properties as 'location'")))
           (c/handle-action (fn [[node local-state] ac]
                              (if (= ::close-action ac)
                                (c/return :state [node (assoc local-state :show? false)])
                                (c/return :action ac)))))))))

(defn- node-component [editable? force-editing? can-focus? can-expand?]
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

                (dom/div
                 (ds/padded-1
                  {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
                  (if-let [type (node-type node)]
                    (pr-type type)
                    "Resource"))


                 (ds/padded-1
                  {:style {:color "#555"
                           :font-size "12px"}}
                  uri))

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
                (dom/button {:onClick ::TODO}
                            "Update with Openstreetmap"))
               (link-organization-with-osm-button))))

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
                               compare-predicate
                               (map-indexed (fn [idx prop]
                                              [(tree/property-predicate prop)
                                               (c/handle-action
                                                (c/focus (lens/at-index idx)
                                                         (property-component editable? editing? can-focus? can-expand?))
                                                (fn [props ac]
                                                  (if (= ::delete ac)
                                                    (c/return :state (remove-index idx props))
                                                    (c/return :action ac)))
                                                )])
                                            props))))))))

         (when editing?
           (add-property-button))))
       ))))

(defn tree-component [editable? force-editing? can-focus? can-expand?]
  (c/with-state-as tree
    (cond
      (tree/node? tree)
      (node-component editable? force-editing? can-focus? can-expand?)

      (tree/literal-string? tree)
      (c/focus tree/literal-string-value
               (if force-editing?
                 (forms/input)
                 (c/dynamic str)))

      (tree/ref? tree)
      (dom/div "REF: " (tree/ref-uri tree)))))

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

(c/defn-item main* [editable? force-editing? can-focus? can-expand?]
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
                                 (tree-component editable? force-editing? can-focus? can-expand?)))
                      trees)))))))

(defn main [editable? force-editing? make-focus-query-action make-expand-by-query-action]
  (let [can-focus? (some? make-focus-query-action)
        can-expand? (some? make-expand-by-query-action)]
    (c/handle-action
     (c/focus tree/graph<->trees (main* editable? force-editing? can-focus? can-expand?))
     (fn [_ ac]
       (c/return :action
                 (cond
                   (record/is-a? focus-query-action ac)
                   (make-focus-query-action (focus-query-action-query ac))

                   (record/is-a? expand-by-query-action ac)
                   (make-expand-by-query-action (expand-by-query-action-query ac))

                   :else
                   ac))))))

(defn readonly [graph & [make-focus-query-action make-expand-by-query-action]]
  (c/isolate-state graph (main false false make-focus-query-action make-expand-by-query-action)))

(defn readwrite [graph & [make-focus-query-action make-expand-by-query-action]]
  (c/isolate-state graph (main true false make-focus-query-action make-expand-by-query-action)))

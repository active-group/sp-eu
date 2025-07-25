(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [reacl-c-basics.pages.core :as routing]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [wisen.common.routes :as routes]
            [wisen.frontend.leaflet :as leaflet]
            [wisen.frontend.spinner :as spinner]
            [wisen.frontend.util :as util]
            [wisen.common.or-error :refer [make-success
                                           success?
                                           success-value
                                           make-error]]
            [clojure.string :as string]
            [wisen.frontend.commit :as commit]
            ["jsonld" :as jsonld]
            [wisen.frontend.create :as create]
            [wisen.frontend.schema :as schema]
            [wisen.common.query :as query]
            [wisen.frontend.localstorage :as ls]
            [cljs.reader :as reader]))

(def-record focus-query-action
  [focus-query-action-query :- query/query])

(defn make-focus-query-action [q]
  (focus-query-action focus-query-action-query q))

#_(def-record area-search-action
  [area-search-action-params])

#_(defn make-area-search-action [x]
  (area-search-action area-search-action-params x))

(def-record semantic-area-search-action
  [semantic-area-search-action-query
   semantic-area-search-action-bbox])

(defn make-semantic-area-search-action [query bbox]
  (semantic-area-search-action semantic-area-search-action-query query
                               semantic-area-search-action-bbox bbox))

(def-record search-response
  [search-response-graph-string :- realm/string
   search-response-uri-order :- (realm/sequence-of realm/string)
   search-response-total-hits :- realm/integer])

(defn sparql-request [query]
  (-> (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query (query/serialize-query query)}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"})
      (ajax/map-ok-response
       (fn [body]
         (let [m (reader/read-string body)]
           (search-response
            search-response-graph-string (:model m)
            search-response-uri-order (:relevance m)
            search-response-total-hits (:total-hits m)))))))

#_(defn area-search! [params]
  (ajax/POST "/osm/search-area"
             {:body (.stringify js/JSON (clj->js params))
              :headers {:content-type "application/json"}}))

(defn semantic-area-search! [params]
  (ajax/POST "/osm/semantic-area-search"
             {:body (.stringify js/JSON (clj->js params))
              :headers {:content-type "application/json"}}))

(defn make-filter-component [add-button-label initial-filter item]
  (c/with-state-as state
    (dom/div
     (if-not state
       (ds/button-secondary {:onClick (constantly initial-filter)}
                            add-button-label)
       (dom/div
        {:style {:display "flex"
                 :gap "16px"}}
        (dom/div
         {:style {:background "rgba(255,255,255,0.5)"
                  :border ds/border
                  :border-radius "8px"
                  :padding "8px 12px"
                  :min-width "320px"}}
         item)
        (ds/button-secondary {:onClick (constantly nil)}
                             ds/x-icon))))))

(defn h5 [lbl]
  (dom/h5 {:style {:margin 0
                   :font-size "1em"}}
          lbl))

(c/defn-item filter-thing-type-component []
  (make-filter-component
   "+ Add type filter"
   query/initial-thing-type-filter

   (dom/div
    (h5 "Type is one of ...")
    (dom/p
     (dom/label
      (c/focus (lens/contains query/organization-type)
               (forms/input {:type "checkbox"}))
      "Organization")
     (dom/label
      (c/focus (lens/contains query/event-type)
               (forms/input {:type "checkbox"}))
      "Event")
     (dom/label
      (c/focus (lens/contains query/offer-type)
               (forms/input {:type "checkbox"}))
      "Offer")))))

(c/defn-item filter-target-group-component []
  (make-filter-component
   "+ Add target group filter"
   query/initial-target-group-filter

   (dom/div
    (h5 "Target group is one of ...")
    (dom/p
     (dom/label
      (c/focus (lens/contains query/elderly-target-group)
               (forms/input {:type "checkbox"}))
      "Elderly")
     (dom/label
      (c/focus (lens/contains query/queer-target-group)
               (forms/input {:type "checkbox"}))
      "Queer")
     (dom/label
      (c/focus (lens/contains query/immigrants-target-group)
               (forms/input {:type "checkbox"}))
      "Immigrants")))))

(c/defn-item quick-search [loading?]
  (c/with-state-as [query show-advanced? :local false]
    (let [everything? (query/everything-query? query)]
      (dom/div
       (dom/div
        {:style {:padding "8px"
                 :display "flex"
                 :gap "12px"
                 :justify-content "center"
                 :align-items "baseline"}}
        (forms/form
         {:onSubmit (fn [[query _show-advanced?] event]
                      (.preventDefault event)
                      (c/return :action (make-focus-query-action query)))
          :style {:display "flex"
                  :align-items "baseline"
                  :margin "0"
                  :gap "10px"
                  :border "1px solid rgba(255,255,255,0.8)"
                  :background "rgba(255,255,255,0.5)"
                  :backdrop-filter "blur(20px)"
                  :padding "4px 8px"
                  :border-radius "48px"
                  :box-shadow "0 2px 8px rgba(0,0,0,0.3)"
                  }}

         (c/focus lens/first
                  (c/focus query/query-fuzzy-search-term
                           (ds/input {:size 28
                                      :style {:opacity (when everything? "0.5")
                                              :border-radius "20px"}})))

         (ds/button-primary {:type "submit"
                             :style {:background "#923dd2"
                                     :padding "6px 16px"
                                     :border-radius "20px"
                                     :color "white"}}
                            (if loading?
                              (dom/div
                               {:style {:display "flex"
                                        :align-items "center"
                                        :gap "0.5em"}}
                               "Searching …"
                               (spinner/main))
                              (str "Search"
                                   (when everything?
                                     " everything")))))

        (c/focus lens/second
                 (ds/button-secondary {:onClick not
                                       :style {:background "white"
                                               :padding "6px 12px"
                                               :border-radius "4px"}}
                                      (c/with-state-as show?
                                        (if show?
                                          "Hide filters"
                                          "Show filters"))))
        #_(forms/form
           {:onSubmit (fn [state event]
                        (.preventDefault event)
                        (c/return :action (make-semantic-area-search-action
                                           (:free-text state)
                                           (:location state))))}
           (dom/div "Freitextsuche")
           (c/focus :free-text
                    (ds/input))
           (ds/button-primary {:type "submit"
                               :style {:background "#923dd2"
                                       :padding "6px 16px"
                                       :border-radius "20px"
                                       :color "white"}}
                              "Search")))

       (when show-advanced?
         (c/focus lens/first
                  (dom/div
                   {:style {:background "rgba(255,255,255,0.5)"
                            :backdrop-filter "blur(20px)"
                            :padding "6px 16px"
                            :border-top "1px solid #bbb"
                            }}
                   (dom/h4 "Additional search filters")

                   (dom/div
                    {:style {:display "flex"
                             :flex-direction "column"
                             :gap "8px"}}
                    (c/focus query/query-filter-thing-type
                             (filter-thing-type-component))

                    (c/focus query/query-filter-target-group
                             (filter-target-group-component))))))))))

(def-record search-response*
  [search-response*-graph ;; parsed rdflib graph
   search-response*-uri-order :- (realm/sequence-of realm/string)
   search-response*-total-hits :- realm/integer
   ])

(defn run-query [q]
  (let [request (sparql-request q)]
    (c/isolate-state
     {}
     (c/with-state-as responses
       (c/fragment
        (c/focus (lens/member request)
                 (ajax/fetch request))

        (when-let [current-response (get responses request)]
          (if (ajax/response-ok? current-response)
            (let [resp-val (ajax/response-value current-response)]
              (util/json-ld-string->graph
               (search-response-graph-string resp-val)
               (fn [response-graph]
                 (c/once
                  (fn [_]
                    (c/return :action (make-success
                                       (search-response*
                                        search-response*-graph response-graph
                                        search-response*-uri-order (search-response-uri-order resp-val)
                                        search-response*-total-hits (search-response-total-hits resp-val)))))))))
            ;; else
            (c/once
             (fn [_]
               (c/return :action (make-error (ajax/response-value current-response))))))))))))

(defn- unwrap-rdf-literal-decimal [x]
  (assert (or (rdf/literal-decimal? x)
              (rdf/literal-string? x)))

  (cond (rdf/literal-decimal? x)
        (js/Number (rdf/literal-decimal-value x))

        (rdf/literal-string? x)
        (.parseFloat js/Number (rdf/literal-string-value x))))

(defn- unwrap-rdf-literal-decimal-tuple [[lat long]]
  [(unwrap-rdf-literal-decimal lat)
   (unwrap-rdf-literal-decimal long)])

(defn- color-for-coordinates [coords]
  (let [hash-value (hash coords)
        num-colors 20
        color-index (mod hash-value num-colors)]
    (case color-index
      0 "#2c2c2c"
      1 "#1a1a1a"
      2 "#333333"
      3 "#4d4d4d"
      4 "#660000"
      5 "#003300"
      6 "#000066"
      7 "#330033"
      8 "#663300"
      9 "#336633"
      10 "#663366"
      11 "#333300"
      12 "#000033"
      13 "#330000"
      14 "#003333"
      15 "#330033"
      16 "#663333"
      17 "#336600"
      18 "#006666"
      19 "#663366"
      )))

(defn- map-label-for-uri [uri]
  (let [ascii-int (+ (.charCodeAt \A 0)
                     (mod (hash uri) 26))]
    (char ascii-int)))

(def ^:private startup-query-local-storage-key
  "startup-query")

(defn- query->string [q]
  (pr-str (query/serialize-query q)))

(defn- string->query [s]
  (query/deserialize-query
   (reader/read-string s)))

(c/defn-item ^:private with-startup-query [item]
  (c/isolate-state
   nil
   (c/with-state-as query
     (cond
       (nil? query)
       ;; try loading intial query from local state
       (c/handle-action
        (ls/get! startup-query-local-storage-key)
        (fn [_ s]
          (try
            (string->query s)
            (catch js/Object e
              query/initial-query))))

       (record/is-a? query/query query)
       (c/fragment
        item
        (c/once
         (fn [q]
           (c/return :action (ls/set! startup-query-local-storage-key
                                      (query->string q))))))))))

(c/defn-item map-search [schema loading? pins]
  (with-startup-query
    (c/fragment

     (c/focus (lens/>> query/query-geo-bounding-box
                       query/geo-bounding-box<->vectors)
              (leaflet/main {:style {:height 560}} pins))

     (dom/div
      {:style {:position "sticky"
               :top 0
               :z-index 999
               :background "#eee"
               :border-bottom ds/border}}

      (quick-search loading?)))))

(declare tree-geo-positions)

(letfn [(unwrap [obj]
          (cond
            (tree/literal-decimal? obj)
            (parse-double (tree/literal-decimal-value obj))

            (tree/literal-string? obj)
            (parse-double (tree/literal-string-value obj))
            ))]
  (defn- node-geo-position [node]
    (let [lat (or ((tree/node-object-for-predicate "http://schema.org/latitude") node)
                  ((tree/node-object-for-predicate "http://www.w3.org/2003/01/geo/wgs84_pos#lat") node))
          long (or ((tree/node-object-for-predicate "http://schema.org/longitude") node)
                   ((tree/node-object-for-predicate "http://www.w3.org/2003/01/geo/wgs84_pos#long") node))]
      (when-let [lt (unwrap lat)]
        (when-let [lng (unwrap long)]
          (let [node-uri (tree/node-uri node)]
            [[lt lng] node-uri]))))))

(defn- node-geo-positions [node]
  (let [poss (mapcat (fn [prop]
                       (tree-geo-positions
                        (tree/property-object prop)))
                     (tree/node-properties node))]
    (if-let [pos (node-geo-position node)]
      (conj poss pos)
      poss)))

(defn- tree-geo-positions [etree]
  (cond
    (tree/ref? etree)
    []

    (tree/literal-string? etree)
    []

    (tree/literal-decimal? etree)
    []

    (tree/literal-boolean? etree)
    []

    (tree/literal-time? etree)
    []

    (tree/literal-date? etree)
    []

    (tree/many? etree)
    (mapcat tree-geo-positions (tree/many-trees etree))

    (tree/exists? etree)
    []

    (tree/node? etree)
    (node-geo-positions etree)))

(c/defn-item main* [schema]
  (c/with-state-as state
    (c/fragment

     ;; may trigger queries
     (-> (c/isolate-state
          (when-let [graph (:graph state)]
            (edit-tree/graph->edit-tree graph))

          (dom/div
           {:style {:display "flex"
                    :flex-direction "column"
                    :overflow "auto"}}

           (dom/div
            {:class "map-and-search-results"
             :style {:overflow "auto"
                     :flex 1
                     :scroll-behavior "smooth"}}

            (c/with-state-as etree
              (map-search schema
                          (some? (:last-focus-query state))
                          (when etree
                            (map (fn [[coords uri]]
                                   (leaflet/make-pin
                                    (map-label-for-uri uri)
                                    (color-for-coordinates coords)
                                    coords
                                    (str "#" uri)))
                                 (tree-geo-positions (edit-tree/edit-tree-result-tree etree))))))

            ;; display when we have a graph
            (c/with-state-as etree
              (when etree
                (ds/padded-2
                 (dom/h2 "Results")
                 (dom/div {:style {:color "#555"
                                   :font-weight "normal"
                                   :margin-bottom "1em"}}
                          (str
                           "Showing 1-"
                           (count (:uri-order state))
                           " of "
                           (:total-hits state)))
                 (dom/div
                  {:style {:padding-bottom "1em"}}
                  (editor/edit-tree-component schema nil true false nil (:uri-order state)
                                              (into {}
                                                    (map (fn [[coords uri]]
                                                           [uri (dom/div
                                                                 {:style {:background (color-for-coordinates coords)
                                                                          :width "20px"
                                                                          :height "20px"
                                                                          :color "white"
                                                                          :font-weight "bold"
                                                                          :border-radius "100%"
                                                                          :text-align "center"}}
                                                                 (map-label-for-uri uri)
                                                                 )])
                                                         (tree-geo-positions (edit-tree/edit-tree-result-tree etree)))))))))

            (when-let [sugg-graphs (:sugg-graphs state)]
              (let [background-color "#e1e1e1"]
                (dom/div
                 {:style {:background background-color
                          :border-top ds/border}}
                 (ds/padded-2
                  (dom/h2 "Results from the web")
                  (apply dom/div {:style {:display "flex"
                                          :flex-direction "column"
                                          :overflow "auto"}}
                         (map (fn [graph]
                                (dom/div
                                 (editor/readonly-graph schema graph background-color)
                                 (modal/modal-button "open editor" (fn [close-action]
                                                                     (let [tree (tree/graph->tree graph)]
                                                                       (create/main schema
                                                                                    tree
                                                                                    (ds/button-secondary
                                                                                     {:onClick (fn [_] (c/return :action close-action))}
                                                                                     "Close")))))))
                              sugg-graphs)))))))

           (c/with-state-as etree
             (when-not (empty? (edit-tree/edit-tree-changeset etree))
               (commit/main schema)))))

         (c/handle-action
          (fn [st ac]
            (cond
              (record/is-a? focus-query-action ac)
              (assoc st :last-focus-query (focus-query-action-query ac))

              #_#_(record/is-a? area-search-action ac)
              (assoc st :area-search-params (area-search-action-params ac)
                     :area-search-response nil)

              (record/is-a? semantic-area-search-action ac)
              (assoc st :semantic-area-search-params {:semantic-area-search-query (semantic-area-search-action-query ac)
                                                      :semantic-area-search-bbox (semantic-area-search-action-bbox ac)}
                     :semantic-area-searech-response nil)
              :else
              (c/return :action ac)))))

     ;; perform focus query
     (when-let [last-focus-query (:last-focus-query state)]
       (c/fragment
        (spinner/main)
        (-> (run-query last-focus-query)
            (c/handle-action (fn [st ac]
                               ;; TODO: error handling
                               (if (success? ac)
                                 (c/return :state
                                           (-> st
                                               (assoc :graph (search-response*-graph (success-value ac)))
                                               (assoc :uri-order (search-response*-uri-order (success-value ac)))
                                               (assoc :total-hits (search-response*-total-hits (success-value ac)))
                                               (dissoc :last-focus-query)))
                                 (c/return :action ac)))))))

     #_(when-let [area-search-params (:area-search-params state)]
       (c/handle-action
        (util/load-json-ld (area-search! area-search-params))
        (fn [st ac]
          ;; TODO: error handling
          (if (success? ac)
            (let [full-graph (success-value ac)
                  components (rdf/get-subcomponents full-graph)]
              (c/return :state
                        (-> st
                            (assoc :sugg-graphs components)
                            (dissoc :area-search-params))))
            (c/return :action ac)))))

     (when-let [semantic-area-search-params (:semantic-area-search-params state)]
       (c/handle-action
        (util/load-json-ld (semantic-area-search! semantic-area-search-params))
        (fn [st ac]
          (if (success? ac)
            (let [full-graph (success-value ac)
                  components (rdf/get-subcomponents full-graph)]
              (c/return :state
                        (-> st
                            (assoc :sugg-graphs components)
                            (dissoc :semantic-area-search-params)))))))))))

(c/defn-item main [schema]
  (c/isolate-state
   {:last-focus-query nil
    :last-expand-by-query nil
    :graph nil
    ;; :area-search-params nil
    ;; :area-search-response nil
    :semantic-area-search-params nil
    :semantic-area-search-response nil}
   (c/with-state-as state
     (c/fragment
      (main* schema)))))

(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.data.record :as record :refer-macros [def-record]]
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
            [wisen.frontend.or-error :refer [make-success
                                             success?
                                             success-value
                                             make-error]]
            [wisen.frontend.commit :as commit]
            ["jsonld" :as jsonld]
            [wisen.frontend.create :as create]
            [wisen.frontend.schema :as schema]))

(def-record focus-query-action
  [focus-query-action-query])

(defn make-focus-query-action [q]
  (focus-query-action focus-query-action-query q))

(def-record area-search-action
  [area-search-action-params])

(defn make-area-search-action [x]
  (area-search-action area-search-action-params x))

(defn sparql-request [query]
  (-> (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query query}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"})
      #_(ajax/map-ok-response
       (fn [body]
         (js/JSON.parse body)))))

(defn- organization->sparql [m]
  (let [[[min-lat max-lat] [min-long max-long]] (:location m)]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s a <http://schema.org/Organization> .
                      ?s <https://wisen.active-group.de/target-group> ?target .
                      ?s <http://schema.org/keywords> ?keywords .
                      ?s <http://schema.org/location> ?location .
                      ?location a <http://schema.org/Place> .
                      ?location <http://schema.org/geo> ?coords .
                      ?coords <http://schema.org/latitude> ?lat .
                      ?coords <http://schema.org/longitude> ?long .
                      ?location ?locationp ?locationo .
                    }
          WHERE { ?s ?p ?o .
                  ?s a <http://schema.org/Organization> .
                  ?s <https://wisen.active-group.de/target-group> ?target .
                  ?s <http://schema.org/keywords> ?keywords .
                  ?s <http://schema.org/location> ?location .
                  ?location a <http://schema.org/Place> .
                  ?location <http://schema.org/geo> ?coords .
                  ?coords <http://schema.org/latitude> ?lat .
                  ?coords <http://schema.org/longitude> ?long .

                      OPTIONAL {
                        ?location ?locationp ?locationo .
                      }
                  FILTER( ?lat >= " min-lat " && ?lat <= " max-lat " && ?long >= " min-long " && ?long <= " max-long " )
                  FILTER(CONTAINS(LCASE(STR(?keywords)), \"" (first (:tags m)) "\"))
                  FILTER(CONTAINS(LCASE(STR(?target)), \"" (:target m) "\"))
                  }")))

(defn- place->sparql [m]
  (let [[[min-lat max-lat] [min-long max-long]] (:location m)]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s a <http://schema.org/Place> .
                      ?s <https://wisen.active-group.de/target-group> ?target .
                      ?s <http://schema.org/keywords> ?keywords .
                      ?s <http://schema.org/geo> ?coords .
                      ?coords <http://schema.org/latitude> ?lat .
                      ?coords <http://schema.org/longitude> ?long .
                    }
          WHERE { ?s ?p ?o .
                  ?s a <http://schema.org/Place> .
                  ?s <https://wisen.active-group.de/target-group> ?target .
                  ?s <http://schema.org/keywords> ?keywords .
                  ?s a <http://schema.org/Place> .
                  ?s <http://schema.org/geo> ?coords .
                  ?coords <http://schema.org/latitude> ?lat .
                  ?coords <http://schema.org/longitude> ?long .
                  FILTER( ?lat >= " min-lat " && ?lat <= " max-lat " && ?long >= " min-long " && ?long <= " max-long " )
                  FILTER(CONTAINS(LCASE(STR(?keywords)), \"" (first (:tags m)) "\"))
                  FILTER(CONTAINS(LCASE(STR(?target)), \"" (:target m) "\"))
                  }")))

(defn- offer->sparql [m]
  (let [[[min-lat max-lat] [min-long max-long]] (:location m)]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s a <http://schema.org/Offer> .
                      ?s <https://wisen.active-group.de/target-group> ?target .
                      ?s <http://schema.org/keywords> ?keywords .
                      ?s <http://schema.org/location> ?location .
                      ?location a <http://schema.org/Place> .
                      ?location <http://schema.org/geo> ?coords .
                      ?coords <http://schema.org/latitude> ?lat .
                      ?coords <http://schema.org/longitude> ?long .
                      ?location ?locationp ?locationo .
                    }
          WHERE { ?s ?p ?o .
                  ?s a <http://schema.org/Offer> .
                  ?s <https://wisen.active-group.de/target-group> ?target .
                  ?s <http://schema.org/keywords> ?keywords .
                  ?s <http://schema.org/availableAtOrFrom> ?location .
                  ?location a <http://schema.org/Place> .
                  ?location <http://schema.org/geo> ?coords .
                  ?coords <http://schema.org/latitude> ?lat .
                  ?coords <http://schema.org/longitude> ?long .

                      OPTIONAL {
                        ?location ?locationp ?locationo .
                      }
                  FILTER( ?lat >= " min-lat " && ?lat <= " max-lat " && ?long >= " min-long " && ?long <= " max-long " )
                  FILTER(CONTAINS(LCASE(STR(?keywords)), \"" (first (:tags m)) "\"))
                  FILTER(CONTAINS(LCASE(STR(?target)), \"" (:target m) "\"))
                  }")))

(defn- event->sparql [m]
  (let [[[min-lat max-lat] [min-long max-long]] (:location m)]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s a <http://schema.org/Event> .
                      ?s <https://wisen.active-group.de/target-group> ?target .
                      ?s <http://schema.org/keywords> ?keywords .
                      ?s <http://schema.org/location> ?location .
                      ?location a <http://schema.org/Place> .
                      ?location <http://schema.org/geo> ?coords .
                      ?coords <http://schema.org/latitude> ?lat .
                      ?coords <http://schema.org/longitude> ?long .
                      ?location ?locationp ?locationo .
                    }
          WHERE { ?s ?p ?o .
                  ?s a <http://schema.org/Event> .
                  ?s <https://wisen.active-group.de/target-group> ?target .
                  ?s <http://schema.org/keywords> ?keywords .
                  ?s <http://schema.org/location> ?location .
                  ?location a <http://schema.org/Place> .
                  ?location <http://schema.org/geo> ?coords .
                  ?coords <http://schema.org/latitude> ?lat .
                  ?coords <http://schema.org/longitude> ?long .

                      OPTIONAL {
                        ?location ?locationp ?locationo .
                      }
                  FILTER( ?lat >= " min-lat " && ?lat <= " max-lat " && ?long >= " min-long " && ?long <= " max-long " )
                  FILTER(CONTAINS(LCASE(STR(?keywords)), \"" (first (:tags m)) "\"))
                  FILTER(CONTAINS(LCASE(STR(?target)), \"" (:target m) "\"))
                  }")))

(defn- quick-search->sparql [m]
  (case (:type m)
    :organization
    (organization->sparql m)

    :place
    (place->sparql m)

    :offer
    (offer->sparql m)

    :event
    (event->sparql m)))

(defn area-search! [params]
  (ajax/POST "/osm/search-area"
             {:body (.stringify js/JSON (clj->js params))
              :headers {:content-type "application/json"}}))

(c/defn-item quick-search [loading?]
  (dom/div
   {:style {:padding "8px"
            :display "flex"
            :justify-content "center"}}
   (forms/form
    {:onSubmit (fn [state event]
                 (.preventDefault event)
                 (c/return :action (make-focus-query-action
                                    (quick-search->sparql state))
                           :action (make-area-search-action (:location state))))
     :style {:display "flex"
             :align-items "baseline"
             :gap "16px"
             :border "1px solid rgba(255,255,255,0.8)"
             :background "rgba(255,255,255,0.5)"
             :backdrop-filter "blur(20px)"
             :padding "10px 32px"
             :border-radius "48px"
             :box-shadow "0 2px 8px rgba(0,0,0,0.3)"
             }}
    (dom/div "I'm looking for ")
    (c/focus :type
             (ds/select
              (forms/option {:value :organization}
                            "organizations")
              (forms/option {:value :place}
                            "places")
              (forms/option {:value :offer}
                            "offers")
              (forms/option {:value :event}
                            "events")))
    (dom/div "targeted towards")
    (c/focus :target
             (ds/select
              (forms/option {:value "elderly"}
                            "elderly")
              (forms/option {:value "queer"}
                            "queer")
              (forms/option {:value "immigrants"}
                            "immigrants")
              ))

    (dom/div "with tag")
    (c/focus (lens/>> :tags lens/first)
             (ds/input))

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
                         "Search")))))

(defn run-query [q]
  (util/load-json-ld (sparql-request q)))

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
  ;; TODO: properly turn hashes into colors
  (case (mod (hash coords) 4)
    0 "blue"
    1 "red"
    2 "green"
    3 "purple"
    ))

(c/defn-item map-search [schema loading? pins]
  (c/isolate-state
   {:type :organization
    :target "elderly"
    :tags ["education"]
    :location [[48.484 48.550]
               [9.0051 9.106]]}
   (dom/div
    {:style {:position "relative"}}
    (dom/div
     {:style {:position "absolute"
              :bottom 0
              :left 0
              :z-index 999
              :width "100%"}}

     (quick-search loading?))

    (c/focus :location
             (leaflet/main {:style {:height 460}} pins)))))

(c/defn-item main* [schema]
  (c/with-state-as state
    (c/fragment

     ;; may trigger queries
     (-> (c/isolate-state
          (when-let [graph (:graph state)]
            (edit-tree/graph->edit-trees graph))

          (dom/div
           {:style {:display "flex"
                    :flex-direction "column"
                    :overflow "auto"}}

           (dom/div
            {:class "map-and-search-results"
             :style {:overflow "auto"
                     :flex 1
                     :scroll-behavior "smooth"}}

            (map-search schema
                        (some? (:last-focus-query state))
                        (when-let [graph (:graph state)]
                          (map (fn [position]
                                 (let [coords (unwrap-rdf-literal-decimal-tuple position)]
                                   (leaflet/make-pin
                                    "A"
                                    (color-for-coordinates coords)
                                    coords)))
                               (rdf/geo-positions graph))))

            ;; display when we have a graph
            (when (:graph state)
              (ds/padded-2
               (editor/edit-trees-component schema true false)))

            (when-let [sugg-graphs (:sugg-graphs state)]
              (apply dom/div {:style {:display "flex"
                                      :flex-direction "column"
                                      :overflow "auto"}}
                     (map (fn [graph]
                            (dom/div
                             (editor/readonly-graph schema graph)
                             (modal/modal-button "open editor" (fn [close-action]
                                                                 (let [trees (tree/graph->trees graph)]
                                                                   (create/main schema
                                                                                (first trees)
                                                                                (ds/button-secondary
                                                                                 {:onClick (fn [_] (c/return :action close-action))}
                                                                                 "Close")))))))
                          sugg-graphs))))

           (c/with-state-as etrees
             (when-not (empty? (edit-tree/edit-trees-changes etrees))
               (commit/main schema)))))

         (c/handle-action
          (fn [st ac]
            (cond
              (record/is-a? focus-query-action ac)
              (assoc st :last-focus-query (focus-query-action-query ac))

              (record/is-a? area-search-action ac)
              (assoc st :area-search-params (area-search-action-params ac)
                     :area-search-response nil)

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
                                               (assoc :graph (success-value ac))
                                               (dissoc :last-focus-query)))
                                 (c/return :action ac)))))))

     (when-let [area-search-params (:area-search-params state)]
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
            (c/return :action ac))))))))

(c/defn-item main [schema]
  (c/isolate-state
   {:last-focus-query nil
    :last-expand-by-query nil
    :graph nil
    :area-search-params nil
    :area-search-response nil}
   (c/with-state-as state
     (c/fragment
      (main* schema)))))

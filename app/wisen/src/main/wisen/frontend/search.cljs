(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.data.record :as record :refer-macros [def-record]]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.leaflet :as leaflet]
            [wisen.frontend.spinner :as spinner]
            ["jsonld" :as jsonld]))

(def-record place [place-label place-bounding-box])

(defn make-place [lbl bb]
  (place place-label lbl
         place-bounding-box bb))

(defn place? [x]
  (record/is-a? place x))

(def-record group [group-label group-places])

(defn make-group [lbl plcs]
  (group group-label lbl
         group-places plcs))

(defn group? [x]
  (record/is-a? group x))

(def search-places
  ;; latitude range, longitude range
  {:berlin (make-group "Berlin"
                       {:berlin
                        (make-place "Berlin" [[53.3 52.7]
                                              [13.0 13.8]])
                        :berlin-friedrichshain-kreuzberg
                        (make-place "Friedrichshain-Kreuzberg" [[52.488 52.531]
                                                                [13.398 13.465]])

                        :berlin-mitte
                        (make-place "Mitte" [[52.494 52.537]
                                             [13.354 13.457]])
                        })
   :ljubljana (make-place "Ljubljana" [[46.011 46.125]
                                       [14.411 14.647]])
   :tuebingen (make-place "Tübingen" [[48.484 48.550]
                                      [9.0051 9.106]])})

(defn flatten-place-map [m]
  (apply merge
         (map (fn [[k v]]
                (cond
                  (place? v)
                  {k v}

                  (group? v)
                  (flatten-place-map (group-places v))))
              m)))

(vals (flatten-place-map search-places))

(defn- geo-range-for-key [k]
  (place-bounding-box
   (get (flatten-place-map search-places)
        k)))

(defn- ->options [m]
  (map (fn [[k v]]
         (cond
           (place? v)
           (forms/option {:value k}
                         (place-label v))

           (group? v)
           (apply forms/optgroup
                  {:label (group-label v)}
                  (->options (group-places v)))))
       m))

(def-record focus-query-action
  [focus-query-action-query])

(defn make-focus-query-action [q]
  (focus-query-action focus-query-action-query q))

(defn sparql-request [query]
  (-> (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query query}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"})
      #_(ajax/map-ok-response
       (fn [body]
         (js/JSON.parse body)))))

(defn quick-search->sparql [m]
  (let [ty (case (:type m)
             :organization "<http://schema.org/Organization>"
             :place "<http://schema.org/Place>"
             :offer "<http://schema.org/Offer>"
             :event "<http://schema.org/Event>")
        [[min-lat max-lat] [min-long max-long]] (:location m)]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s a " ty ".
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
                  ?s a " ty ".
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

(c/defn-item quick-search [loading?]
  (dom/div
   {:style {:padding "8px"
            :display "flex"
            :justify-content "center"}}
   (forms/form
    {:onSubmit (fn [state event]
                 (.preventDefault event)
                 (c/return :action (make-focus-query-action
                                    (quick-search->sparql state))))
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
                         "Search"))

    #_(dom/div
     {:style {:position "relative"
              :top "2px"
              :margin-left "-8px"
              :margin-right "-20px"}}
     (spinner/main)))))

(defn run-query [q]
  (c/isolate-state
   nil
   (c/fragment
    (ajax/fetch (sparql-request q))

    (c/with-state-as response
      (when (ajax/response? response)
        (if (ajax/response-ok? response)
          (promise/call-with-promise-result
           (rdf/json-ld-string->graph-promise (ajax/response-value response))
           (fn [response-graph]
             (c/once
              (fn [_]
                (c/return :action (ajax/ok-response response-graph))))))
          (c/once
           (fn [_]
             (c/return :action response)))))))))

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

(c/defn-item main* [schema]
  (c/with-state-as state
    (c/fragment

     ;; may trigger queries
     (-> (dom/div
          {:style {:display "flex"
                   :flex-direction "column"
                   :overflow "auto"}}

          (dom/div
           {:style {:border-bottom ds/border}}
           (dom/div

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

               (quick-search (some? (:last-focus-query state))))

              (c/focus :location
                       (leaflet/main {:style {:height 460}}
                                     (when-let [graph (:graph state)]
                                       (map (fn [position]
                                              (let [coords (unwrap-rdf-literal-decimal-tuple position)]
                                                (leaflet/make-pin
                                                 "A"
                                                 (color-for-coordinates coords)
                                                 coords)))
                                            (rdf/geo-positions graph))))))))

           ;; display when we have a graph
           (when-let [graph (:graph state)]
             (ds/padded-2
              (editor/edit-graph schema true false graph)))))

         (c/handle-action
          (fn [st ac]
            (if (record/is-a? focus-query-action ac)
              (assoc st :last-focus-query (focus-query-action-query ac))
              (c/return :action ac)))))

     ;; perform focus query
     (when-let [last-focus-query (:last-focus-query state)]
       (c/fragment
        (spinner/main)
        (-> (run-query last-focus-query)
            (c/handle-action (fn [st ac]
                               ;; TODO: error handling
                               (if (and (ajax/response? ac)
                                        (ajax/response-ok? ac))
                                 (c/return :state
                                           (-> st
                                               (assoc :graph (ajax/response-value ac))
                                               (dissoc :last-focus-query)))
                                 (c/return :action ac))))))))))

(c/defn-item main [schema]
  (c/isolate-state
   {:last-focus-query nil
    :last-expand-by-query nil
    :graph nil}
   (c/with-state-as state
     (c/fragment
      (main* schema)))))

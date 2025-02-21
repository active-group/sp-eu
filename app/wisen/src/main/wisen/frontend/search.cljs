(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.display :as display]
            [wisen.frontend.routes :as routes]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            ["jsonld" :as jsonld]))


(c/defn-item query-form "has no public state" []
  (c/isolate-state
   "CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }"
   (forms/form {:style {:margin 0}
                :onSubmit (fn [state event]
                            (.preventDefault event)
                            (c/return :action state :state ""))}
               (dom/div
                {:style {:display "flex"
                         :gap "16px"}}
                (forms/input {:type "search"
                              :placeholder "Search query"
                              :style {:flex 1
                                      :border "1px solid #d3d3d3"
                                      :padding "8px 16px"
                                      :border-radius "20px"}})
                (dom/button "Search")))))

(defn search-request [query]
  (-> (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query query}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"})
      #_(ajax/map-ok-response
       (fn [body]
         (js/JSON.parse body)))))

(c/defn-item display-search-results [json-string]
  (promise/call-with-promise-result (rdf/json-ld-string->graph-promise json-string)
                                    display/readonly))

(defn quick-search->sparql [m]
  (let [ty (case (:type m)
             :organization "<http://schema.org/Organization>"
             :place "<http://schema.org/Place>"
             :offer "<http://schema.org/Offer>"
             :event "<http://schema.org/Event>")]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?o ?p2 ?o2 .
                      ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " ty ".
                      ?s <http://schema.org/keywords> ?keywords . }
          WHERE { ?s ?p ?o .
                  ?o ?p2 ?o2 .
                  ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " ty ".
                  ?s <http://schema.org/keywords> ?keywords .
                  FILTER(CONTAINS(LCASE(STR(?keywords)), \"" (first (:tags m)) "\")) }")))

(c/defn-item quick-search []
  (c/isolate-state {:type :organization
                    :target :elderly
                    :tags ["education"]}
                   (forms/form
                    {:onSubmit (fn [state event]
                                 (.preventDefault event)
                                 (c/return :action (quick-search->sparql state)))
                     :style {:display "flex"
                             :gap "16px"}}
                    (dom/div "I'm looking for ")
                    (c/focus :type
                             (forms/select
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
                             (forms/select
                              (forms/option {:value :elderly}
                                            "elderly")
                              (forms/option {:value :queer}
                                            "queer")
                              (forms/option {:value :immigrants}
                                            "immigrants")
                              ))

                    (dom/div "with tag")
                    (c/focus (lens/>> :tags lens/first)
                             (forms/input))

                    (dom/button {:type "submit"} "Search"))))

(c/defn-item main* []
  (c/with-state-as state
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :overflow "auto"}}

     (ds/padded-2
      {:style {:border-bottom ds/border}}
      (c/handle-action
       (dom/div
        (query-form)
        (quick-search))
       (fn [st query]
         (c/return :state (assoc st :last-query query)))))

     ;; perform search queries
     (when-let [last-query (:last-query state)]
       (c/focus (lens/>> :results
                         (lens/member last-query))
                (c/fragment
                 (ajax/fetch (search-request last-query))

                 ;; continue with successful search results
                 (c/with-state-as response
                   (when (and (ajax/response? response)
                              (ajax/response-ok? response))
                     (dom/div
                      {:style {:overflow "auto"}}
                      (ds/padded-2
                       (dom/h2 "Search Results")
                       (display-search-results (ajax/response-value response))))
                     ))))))))

(c/defn-item main []
  (c/isolate-state
   {:last-query nil
    :results {}}
   (main*)))

(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.display :as display]
            [wisen.frontend.routes :as routes]
            ["jsonld" :as jsonld]))

(c/defn-item query-form "has no public state" []
  (c/isolate-state
    "CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }"
    (forms/form {:onSubmit (fn [state event]
                             (.preventDefault event)
                             (c/return :action state :state ""))}
                (forms/input {:placeholder "Search query"})
                (dom/button "Search"))))

(defn search-request [query]
  (-> (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query query}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"})
      (ajax/map-ok-response
       (fn [body]
         (js/JSON.parse body)))))

(c/defn-item display-search-results [result-json]
  (promise/call-with-promise-result (jsonld/expand result-json)
                                    display/display-expanded))

(c/defn-item main* []
  (c/with-state-as state
    (dom/div
     (c/handle-action
      (query-form)
      (fn [st query]
        (c/return :state (assoc st :last-query query))))

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
                      (dom/h2 "Search Results")
                      (display-search-results (ajax/response-value response)))
                     ))))))))

(c/defn-item main []
  (c/isolate-state
   {:last-query nil
    :results {}}
   (main*)))

(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.data.record :as record :refer-macros [def-record]]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.routes :as routes]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            ["jsonld" :as jsonld]))

(def-record focus-query-action
  [focus-query-action-query])

(defn make-focus-query-action [q]
  (focus-query-action focus-query-action-query q))

(def-record expand-by-query-action
  [expand-by-query-action-query])

(defn make-expand-by-query-action [q]
  (expand-by-query-action expand-by-query-action-query q))

(c/defn-item query-form "has no public state" []
  (c/isolate-state
   "CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }"
   (forms/form {:style {:margin 0}
                :onSubmit (fn [state event]
                            (.preventDefault event)
                            (c/return :action (make-focus-query-action state) :state ""))}
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
             :event "<http://schema.org/Event>")]
    (str "CONSTRUCT { ?s ?p ?o .
                      ?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " ty ".
                      ?s <http://schema.org/keywords> ?keywords . }
          WHERE { ?s ?p ?o .
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
                                 (c/return :action (make-focus-query-action
                                                    (quick-search->sparql state))))
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

(c/defn-item main* []
  (c/with-state-as state
    (c/fragment

     ;; may trigger queries
     (-> (dom/div
          {:style {:display "flex"
                   :flex-direction "column"
                   :overflow "auto"}}

          (ds/padded-2
           {:style {:border-bottom ds/border}}
           (dom/div
            (query-form)
            (quick-search))

           ;; display when we have a graph
           (when-let [graph (:graph state)]
             (editor/readonly graph make-focus-query-action make-expand-by-query-action))
           ))

         (c/handle-action
          (fn [st ac]
            (cond
              (record/is-a? focus-query-action ac)
              (c/return :state (assoc st :last-focus-query (focus-query-action-query ac)))

              (record/is-a? expand-by-query-action ac)
              (c/return :state (assoc st :last-expand-by-query (expand-by-query-action-query ac)))

              :else
              (c/return :action ac)))))

     ;; perform focus query
     (when-let [last-focus-query (:last-focus-query state)]
       (-> (run-query last-focus-query)
           (c/handle-action (fn [st ac]
                              ;; TODO: error handling
                              (if (and (ajax/response? ac)
                                       (ajax/response-ok? ac))
                                (c/return :state
                                          (-> st
                                              (assoc :graph (ajax/response-value ac))
                                              (dissoc :last-focus-query)))
                                (c/return :action ac))))))

     ;; perform expand-by query
     (when-let [last-expand-by-query (:last-expand-by-query state)]
       (-> (run-query last-expand-by-query)
           (c/handle-action (fn [st ac]
                              ;; TODO: error handling
                              (if (and (ajax/response? ac)
                                       (ajax/response-ok? ac))
                                (c/return :state
                                          (-> st
                                              (update :graph
                                                      (fn [g]
                                                        (rdf/merge g (ajax/response-value ac))))
                                              (dissoc :last-expand-by-query)))
                                (c/return :action ac)))))))))

(c/defn-item main []
  (c/isolate-state
   {:last-focus-query nil
    :last-expand-by-query nil
    :graph nil}
   (main*)))

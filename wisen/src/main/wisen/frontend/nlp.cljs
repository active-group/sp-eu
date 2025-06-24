(ns wisen.frontend.nlp
  (:require [active.clojure.lens :as lens]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c-basics.ajax :as ajax]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.promise :as promise]))

(c/defn-item query-form []
  (c/isolate-state
   "At Spieltreff Tübingen cool people meet to play board games every thursday around 6 pm."
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

(defn llm-query! [query]
  (ajax/POST "/describe"
    {:body query}))

(c/defn-item main* []
  (c/with-state-as state
    (dom/div
     (c/handle-action
      (dom/div
       (query-form))
      (fn [st query]
        (c/return :state {:query query :response nil, :graph nil})))
      (when-let [query (:query state)]
        (c/focus :response
                 (c/fragment
                  (ajax/fetch (llm-query!  query))
                  (c/with-state-as response
                    (when (and (ajax/response? response)
                               (ajax/response-ok? response))
                       (let [resp (ajax/response-value response)
                             invalid-nodes (:invalid-nodes resp)
                             _ (println resp)]
                         (dom/div
                           (when invalid-nodes invalid-nodes)
                           (promise/call-with-promise-result
                            (rdf/json-ld-string->graph-promise (:json-ld-string resp))
                            (fn [response-graph]
                             (editor/readonly-graph response-graph :foo :bar)))))))))))))

(c/defn-item main []
  (c/isolate-state
   {:query nil
   :response nil
   :graph nil}
   (main*)))

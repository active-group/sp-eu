(ns wisen.frontend.ask-ai
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.forms :as wisen.forms]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.frontend.util :as util]
            [wisen.common.or-error :refer [success? success-value error? error-value]]
            [wisen.frontend.spinner :as spinner]))

(def prompt-background "#eee")

;; actions

(def-record insert-action
  [insert-action-graph])

(def-record replace-action
  [replace-action-graph])

;; state

(def pending nil)

(def-record history-item
  [history-item-prompt :- realm/string
   history-item-answer :- (realm/union (realm/enum pending)
                                       ;; response
                                       realm/any)])

(def-record state
  [state-history :- (realm/sequence-of history-item)
   state-current-prompt :- [realm/string wisen.forms/selection-info]])

(defn- serialize-history-item [hit]
  (let [user {:role "user"
              :content (history-item-prompt hit)}
        assistant (when-let [answer (history-item-answer hit)]
                    (when (and (ajax/response? answer)
                               (ajax/response-ok? answer))
                      {:role "assistant"
                       :content (ajax/response-value answer)}))]
    (if assistant
      [user assistant]
      [user])))

(defn- serialize-history [hist]
  {:history
   (mapcat serialize-history-item
           hist)})

(defn make-state [initial-prompt]
  (state state-history []
         state-current-prompt
         (let [len (count initial-prompt)]
           [initial-prompt
            (wisen.forms/make-selected len len)])))

(defn- state-pending? [st]
  (first (filter #(= pending (history-item-answer %))
                 (state-history st))))

(defn- state-run-prompt [st]
  (let [current-prompt (state-current-prompt st)]
    (-> st
        (lens/overhaul state-history
                       (fn [hist]
                         (conj hist (history-item history-item-prompt (first current-prompt)
                                                  history-item-answer pending))))
        (state-current-prompt ["" (wisen.forms/make-selected 0 0)]))))

(defn llm-query [history]
  (ajax/map-ok-response
   (ajax/POST "/ask" {:body (js/JSON.stringify (clj->js (serialize-history history)))
                      :headers {:content-type "application/json"}})
   :json-ld-string))

(defn- prompt-prefix [type]
  (str "The type is <" type ">."))

(c/defn-item call-with-graph [answer k]
  (c/isolate-state
   nil
   (c/with-state-as graph
     (if graph
       (k graph)
       ;; else

       (util/json-ld-string->graph
        answer
        (fn [response-graph]
          (c/once
           (fn [_]
             (c/return :state response-graph)))))))))

(c/defn-item history-item-component [show-graph history]
  (c/with-state-as history-item
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :gap "1ex"}}

     (dom/div
      {:style {:background prompt-background
               :align-self "flex-end"
               :padding "1ex 1em"
               :border-radius "1em"
               :white-space "pre-wrap"}}
      (history-item-prompt history-item))

     (dom/div
      (c/focus history-item-answer
               (c/with-state-as answer
                 (cond
                   (= answer pending)
                   (c/fragment
                    (ajax/fetch (llm-query history))
                    (spinner/main))

                   ;; got answer
                   :else
                   (if (and (ajax/response? answer)
                            (ajax/response-ok? answer))
                     (call-with-graph
                      (ajax/response-value answer)
                      (fn [graph]
                        (dom/div
                         {:style {:padding "1ex 1em"
                                  :background "#eee"
                                  :border "1px solid gray"
                                  :border-radius "8px"}}
                         (dom/div
                          {:style {:display "flex"
                                   :justify-content "space-between"}}
                          (dom/h3 "Result")
                          (dom/div
                           {:style {:display "flex"
                                    :gap "1em"}}
                           (ds/button-primary {:onClick (fn []
                                                          (c/return :action (insert-action insert-action-graph graph)))}
                                              "Insert")
                           (ds/button-primary {:onClick (fn []
                                                          (c/return :action (replace-action replace-action-graph graph)))}
                                              "Replace")))
                         (show-graph graph))))
                     (dom/div
                      (dom/h3 "Error")
                      (dom/pre (pr-str (error-value answer)))))
                   )))))))

(c/defn-item main [show-graph close-action]
  (c/with-state-as node
    (-> (c/local-state

         (make-state
          (prompt-prefix
           (tree/node-uri
            (edit-tree/node-type node))))

         (c/focus lens/second
                  (dom/div
                   {:style {:display "flex"
                            :flex-direction "column"
                            :gap "2ex"}}
                   (c/focus state-history
                            (c/with-state-as history
                              (apply
                               dom/div
                               {:style {:display "flex"
                                        :flex-direction "column"
                                        :gap "2ex"}}

                               (map-indexed
                                (fn [idx _hit]
                                  (c/focus (lens/at-index idx)
                                           (history-item-component show-graph (take (inc idx) history))))
                                history))))

                   (dom/div
                    {:style {:background prompt-background
                             :border-radius "2ex"
                             :padding "1ex 1em"}}
                    (c/focus state-current-prompt
                             (ds/textarea+focus {:style {:min-height "10em"
                                                         :width "100%"
                                                         :background prompt-background
                                                         :border "none"
                                                         :outline "none"}}))
                    (dom/div
                     {:style {:display "flex"
                              :justify-content "flex-end"}}
                     (ds/button-primary {:onClick state-run-prompt}
                                        "Submit"))))))

        (c/handle-action
         (fn [enode ac]
           (cond
             (is-a? replace-action ac)
             (c/return :state
                       (edit-tree/graph->addit-tree (replace-action-graph ac))
                       :action
                       close-action)

             (is-a? insert-action ac)
             (c/return :state
                       (edit-tree/insert-properties enode (tree/tree-properties
                                                           (tree/graph->tree (insert-action-graph ac))))
                       :action
                       close-action)

             :else
             (c/return :action ac)))))))

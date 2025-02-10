(ns wisen.app
  (:require [reacl-c-basics.forms.core :as forms]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [wisen.resource :as r]))

(defn init []
  (js/console.log "init"))

(c/defn-item query-form "has no public state" []
  (c/isolate-state
    ""
    (forms/form {:onSubmit (fn [state event]
                             (.preventDefault event)
                             (println (pr-str state))
                             (c/return :action state :state ""))}
                (forms/input {:placeholder "Search query"})
                (dom/button "Search"))))

(c/defn-item toplevel []
  (c/with-state-as state
    (dom/div
     {:style {:background "grey"}}
     (c/dynamic pr-str)

     (c/handle-action
      (query-form)
      (fn [st query]
        (c/return :action
                  ::TODO
                  #_(channel-push
                           (:channel state)
                           "search"
                           #js {:query query})))))))

(cmain/run
  (.getElementById js/document "main")
  (toplevel)
  {:initial-state ::garnix})

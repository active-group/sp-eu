(ns wisen.frontend.app
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]))

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

(defn search-request [query]
  (ajax/POST "/search"
             {:body {:query query}
              :keywords? false
              :response-format :json}))

(c/defn-item toplevel []
  (c/with-state-as state
    (dom/div
     {:style {:background "grey"}}
     (c/dynamic pr-str)

     (c/handle-action
      (query-form)
      (fn [st query]
        (c/return :state (assoc st :last-query query))))

     ;; perform search queries
     (when-let [last-query (:last-query state)]
       (c/focus (lens/>> :results
                         (lens/member last-query))
                (ajax/fetch (search-request last-query)))))))

(cmain/run
  (.getElementById js/document "main")
  (toplevel)
  {:initial-state {:last-query nil
                   :results {}}})

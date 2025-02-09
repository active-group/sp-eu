(ns wisen.app
  (:require [reacl-c-basics.forms.core :as forms]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [wisen.resource :as r]
            ["phoenix" :as phx]))

(defn init []
  (js/console.log "init"))

(defonce socket
  (new phx/Socket "/sock" #js {:params #js {:token js/window.-token}}))

(defn channel-from-socket [sock]
  (.connect sock)
  (let [^phx/Channel channel-1 (.channel sock "room:lobby" #js {})
        ^phx/Channel channel-2 (.join channel-1)
        ^phx/Channel channel-3 (.receive channel-2
                                         "ok"
                                         (fn [resp]
                                           (println "SUCCESS")
                                           (println (pr-str resp))))
        ^phx/Channel channel-4 (.receive channel-3
                                         "error"
                                         (fn [resp]
                                           (println "ERROR")
                                           (println (pr-str resp))))]

    channel-1))

(c/defn-subscription channel-on deliver! [^phx/Channel chann cmd]
  (let [handler (fn [payload]
                  (deliver! payload))]

    (.on chann cmd handler)
    #()))

(c/defn-effect channel-push [^phx/Channel chann cmd js-payload]
  (.push chann cmd js-payload))

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
        (c/return :action (channel-push
                           (:channel state)
                           "search"
                           #js {:query query}))))

     (c/focus :message
              (c/handle-action
               (channel-on (:channel state) "new-state")
               (fn [st ac]
                 (c/return :state (js->clj ac)))))

     #_(dom/strong "Hello!")
     #_(dom/button {:onclick (fn [_]
                             (c/return :action (channel-push (:channel state)
                                                             "new-msg"
                                                             #js {:body "ja moin"})))}
                 "Send a message"))))

(cmain/run
  (.getElementById js/document "main")
  (toplevel)
  {:initial-state {:socket socket
                   :channel (channel-from-socket socket)}})

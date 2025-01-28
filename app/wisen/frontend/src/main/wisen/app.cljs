(ns wisen.app
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            ["phoenix" :as phx]))


(defonce socket
  (new phx/Socket "/sock" #js {:params #js {:token js/window.-token}}))

(defn init []
  (js/console.log socket)
  (.connect socket)
  (let [channel (.channel socket "room:lobby" #js {})]
    (js/console.log channel)
    (-> channel
        (.join)
        (.receive "ok" (fn [resp]
                         (println "SUCCESS")
                         (println (pr-str resp))))
        (.receive "error" (fn [resp]
                            (println "ERROR")
                            (println (pr-str resp)))))))

(defn toplevel []
  (dom/strong "Hello!"))

(cmain/run
  (.getElementById js/document "main")
  (toplevel))

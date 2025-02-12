(ns wisen.backend.server
  (:require [ring.adapter.jetty :as ring-jetty]
            [wisen.backend.handler :as handler]))

(defonce server (atom nil))

(defn start! []
  (reset! server
          (ring-jetty/run-jetty
           #'handler/handler
           {:port 4321
            :join? false})))

(defn stop! []
  (when-some [jetty @server]
    (reset! server nil)
    (.stop jetty)))

(ns wisen.backend.server
  (:require [ring.adapter.jetty :as ring-jetty]
            [wisen.backend.config :as config]
            [wisen.backend.handler :as handler]))

(defonce server (atom nil))

(defn start!
  "Given command-line configuration options `opts`, start the wisen server."
  [config-path]
  (let [cfg (config/try-load-config config-path false)]
    (reset! server
            (ring-jetty/run-jetty
             (handler/handler cfg)
             {:port 4321
              :join? false}))))

(defn stop! []
  (when-some [jetty @server]
    (reset! server nil)
    (.stop jetty)))

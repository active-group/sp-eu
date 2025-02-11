(ns wisen.backend.repl
  (:require [wisen.backend.core :as core]))

(defn start! []
  (core/start-server!))

(defn stop! []
  (core/stop-server!))

(defn restart! []
  (stop!)
  (start!))

#_(restart!)

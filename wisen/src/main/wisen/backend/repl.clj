(ns wisen.backend.repl
  (:require [wisen.backend.server :as server]))

(defn start! []
  (server/start! "./etc/config.edn"))

(defn stop! []
  (server/stop!))

(defn restart! []
  (stop!)
  (start!))

(restart!)

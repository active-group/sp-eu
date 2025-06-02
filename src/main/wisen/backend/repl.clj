(ns wisen.backend.repl
  (:require [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store]))

(defn start! []
  (triple-store/setup!)
  (server/start!))

(defn stop! []
  (server/stop!))

(defn restart! []
  (stop!)
  (start!))

(restart!)

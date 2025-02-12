(ns wisen.backend.main
  (:gen-class)
  (:require [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store]))

(defn -main
  [& args]
  (println "Wisen Store")
  (triple-store/setup!)
  (server/start!))

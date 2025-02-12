(ns wisen.backend.core
  (:gen-class)
  (:require [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store])
  (:import
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Wisen Store")
  (triple-store/setup!)
  (server/start!))

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

(triple-store/run-select-query!
 "SELECT ?x WHERE { ?x <https://schema.org/name> \"Stadtseniorenrat Tübingen e.V.\"}")

(triple-store/run-construct-query!
 "CONSTRUCT {<http://wisen.active-group.de/resource/b9876> ?p ?o .} WHERE { <http://wisen.active-group.de/resource/b9876> ?p ?o . }")

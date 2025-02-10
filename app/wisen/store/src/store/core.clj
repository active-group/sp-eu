(ns store.core
  (:gen-class)
  (:import
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Wisen Store")

  (let [model (ModelFactory/createDefaultModel)
        hirsch (.createResource model "http://wisen.active-group.de/resource/a12345")
        _ (.addProperty hirsch SchemaDO/name "Hirsch Begegnungsstätte für Ältere e.V.")]
    (.write model System/out "JSON-LD")))

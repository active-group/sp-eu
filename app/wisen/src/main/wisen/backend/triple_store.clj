(ns wisen.backend.triple-store
  (:require [wisen.backend.core :as core]
            [wisen.backend.jsonld]
            [wisen.backend.skolem :as skolem])
  (:import
   (org.apache.jena.tdb2 TDB2 TDB2Factory)
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)
   (org.apache.jena.query ReadWrite QueryExecutionFactory)))

(defonce dataset (atom nil))

(defn- with-write-model!
  ([f]
   (with-write-model! @dataset f))
  ([dataset f]
   (.begin dataset ReadWrite/WRITE)
   (let [model (.getDefaultModel dataset)]
     (try
       (f model)
       (.commit dataset)
       (catch Exception e
         (println (pr-str e))
         (.abort dataset))
       (finally
         (.end dataset))))))

(defn- with-read-model!
  ([f]
   (with-read-model! @dataset f))
  ([dataset f]
   (.begin dataset ReadWrite/READ)
   (let [model (.getDefaultModel dataset)]
     (try
       (f model)
       (catch Exception e
         (.abort dataset))
       (finally
         (.end dataset))))))


(defn- gather-vars [acc solution var-names]
  (if (.hasNext var-names)
    (let [var-name (.next var-names)]
      (recur (assoc acc var-name (.get solution var-name)) solution var-names))
    ;; else
    acc))

(defn- gather-results [acc results]
  (if (.hasNext results)
    (let [sol (.nextSolution results)
          vars (gather-vars {} sol (.varNames sol))]
      (recur (conj acc vars) results))
    ;; else
    acc
    ))

(defn run-select-query!
  "Run a SPARQL SELECT query, returning a list of maps associating
  variable names with resources"
  ([q]
   (with-read-model! (fn [model] (run-select-query! model q))))
  ([model q]
   (let [qexec (QueryExecutionFactory/create q model)
         results (.execSelect qexec)]
     (gather-results '() results))))

(defn run-construct-query!
  "Run a SPARQL SELECT query"
  ([q]
   (with-read-model! (fn [model] (run-construct-query! model q))))
  ([model q]
   (let [qexec (QueryExecutionFactory/create q model)
         graph (.execConstruct qexec)]
     graph)))

(defn add-model!
  ([model-to-add]
   (with-write-model!
     (fn [base-model]
       (add-model! base-model model-to-add))))
  ([base-model model-to-add]
   (.add base-model (skolem/skolemize-model model-to-add "foobar"))))

(defn edit-model!
  ([base-query replacing-model]
   (with-write-model!
     (fn [base-model]
       (edit-model! base-model base-query replacing-model))))
  ([base-model base-query replacing-model]
   (let [replaced-model (run-construct-query! base-model base-query)]
     (.deleteAll replaced-model)
     (.add replaced-model replacing-model))))

(defn- populate! [model]
  (let [mdl (wisen.backend.jsonld/json-ld-string->model
             "{\"@id\": \"http://example.org/hirsch\",
     \"@type\": \"http://schema.org/Organization\",
     \"http://schema.org/name\": \"Begegnungsstätte Hirsch e.V.\",
     \"http://schema.org/description\": \"Wir sind ein Verein von aktiven älteren und jüngeren Menschen, die dieses Haus seit 1982 betreiben, mit Hilfe weniger bezahlter Mitarbeiter*innen. Neben diesen Hauptamtlichen bringen sich etwa 100 Ehrenamtliche in den unterschiedlichsten Bereichen ein – im Vorstand, in der Cafeteria, als Kurs- und Gruppenleiter oder als Referenten. Die Zielgruppe sind vorwiegend ältere Menschen, aber jeder ist willkommen. Angebote gibt es im Bereich der Erwachsenenbildung: Vorträge, Diskussionsrunden zu aktuellen Themen, Sprachkurse, Gesprächskreise sowie Kurse im kreativen, sportlichen und Freizeitbereich. Die Cafeteria mit ca. 80 Plätzen bietet Möglichkeiten für informelle Kontakte, öffentliche PC-Nutzung und Internetzugang, Spiel und Gespräche bei Bewirtung.\",
     \"http://schema.org/keywords\": \"education, fun, play\",
     \"http://schema.org/areaServed\":
        {\"@type\": \"http://schema.org/GeoCircle\", \"http://schema.org/geoMidpoint\": {\"@type\": \"http://schema.org/GeoCoordinates\", \"http://schema.org/latitude\": 48, \"http://schema.org/longitude\": 9}}}")]
    (add-model! model mdl)))

(def dbname "devdb")

(defn setup! []
  (swap! dataset
         (fn [ds]
           (if ds
             ds
             (let [new-ds (TDB2Factory/connectDataset dbname)]
               (with-write-model! new-ds populate!)
               new-ds)))))


#_(run-select-query!
 "SELECT ?x WHERE { ?x <https://schema.org/name> \"Stadtseniorenrat Tübingen e.V.\"}")

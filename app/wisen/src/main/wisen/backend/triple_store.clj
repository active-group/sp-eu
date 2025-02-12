(ns wisen.backend.triple-store
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

(defn- populate! [model]
  (let [hirsch (.createResource model "http://wisen.active-group.de/resource/a12345")
        _ (.addProperty hirsch SchemaDO/name "Hirsch Begegnungsstätte für Ältere e.V.")
        _ (.addProperty hirsch SchemaDO/email "hirsch-begegnung@t-online.de")
        stadtseniorenrat (.createResource model "http://wisen.active-group.de/resource/b9876")
        _ (.addProperty stadtseniorenrat SchemaDO/name "Stadtseniorenrat Tübingen e.V.")
        _ (.addProperty stadtseniorenrat SchemaDO/email "info@stadtseniorenrat-tuebingen.de")
        _ (.addProperty stadtseniorenrat SchemaDO/url "https://www.stadtseniorenrat-tuebingen.de")]
    model))

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

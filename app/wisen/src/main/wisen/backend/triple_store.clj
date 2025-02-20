(ns wisen.backend.triple-store
  (:require [wisen.backend.core :as core])
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
   (.add base-model model-to-add)))

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
  (let [hirsch (.createResource model (str core/*base* "/resource/186fe001-7291-417e-ad9c-58fa6a8240bb"))
        _ (.addProperty hirsch SchemaDO/name "Hirsch Begegnungsstätte für Ältere e.V.")
        _ (.addProperty hirsch SchemaDO/email "hirsch-begegnung@t-online.de")
        stadtseniorenrat (.createResource model (str core/*base* "/resource/b87eb15d-b1e8-4a63-a5ab-3661626ab32f"))
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

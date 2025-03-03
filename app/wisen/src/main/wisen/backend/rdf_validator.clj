(ns wisen.backend.rdf-validator
  (:require [wisen.backend.jsonld :as jsonld])
  (:import (org.apache.jena.shacl ShaclValidator Shapes)
           (org.apache.jena.riot RDFDataMgr)
           (org.apache.jena.riot Lang)
           (org.apache.jena.shacl.lib ShLib)))

(def shapes-path "datashapesorg.ttl")

(defn read-shapes-graph [path]
  (-> path
      (RDFDataMgr/loadGraph)
      (Shapes/parse)))

(defn json-ld-string->graph [s]
  (-> s
      (jsonld/json-ld-string->model)
      (.getGraph)))

(defn validate-data-graph
  ([data-graph shapes]
   (let [report (.validate (ShaclValidator/get) shapes data-graph)]
     (if (.conforms report)
       {:conforms true}
       {:conforms false
        :invalid-nodes (pr-str (.getEntries report))
        :report report})))
  ([data-graph]
   (validate-data-graph data-graph (read-shapes-graph shapes-path))))

(defn validate-json-ld-string [json-ld-string]
  (-> json-ld-string
      (json-ld-string->graph)
      (validate-data-graph)))

(defn validate-model [model]
  (-> model
      (.getGraph)
      (validate-data-graph)))

           ;; (org.apache.jena.rdf.model ModelFactory)
           ;; (org.apache.jena.reasoner ReasonerRegistry)
           ;; (org.apache.jena.vocabulary RDF RDFS)
           ;; (org.apache.jena.reasoner.rulesys RDFSRuleReasonerFactory)
           ;; (org.apache.jena.reasoner ValidityReport)


;; (defn load-shapes-model
;;   "Load the SHACL shapes model from a TTL file"
;;   [shapes-file]
;;   (let [shapes-model (ModelFactory/createDefaultModel)]
;;     (with-open [input-stream (io/input-stream shapes-file)]
;;       (RDFDataMgr/read shapes-model input-stream Lang/TURTLE))
;;     shapes-model))

;; (defn create-inference-model
;;   "Create an inference model using the provided schema model and data model"
;;   [schema-model data-model]
;;   (let [reasoner (-> (ReasonerRegistry/getRDFSReasoner)
;;                      (.bindSchema schema-model))]
;;     (ModelFactory/createInfModel reasoner data-model)))

;; (defn get-unique-predicates [model]
;;   (let [statements (iterator-seq (.listStatements model))]
;;     (->> statements
;;          (map #(.getPredicate %))
;;          (distinct)
;;          (map #(.getURI %)))))

;; (defn get-predicates
;;   "Extracts all unique predicates from a given RDF model."
;;   [model]
;;   (->> (iterator-seq (.listStatements model))
;;        (map #(.getPredicate %))
;;        (distinct)))

;; (defn get-schema-properties
;;   "Extracts all defined properties in the schema model."
;;   [schema-model]
;;   (->> (iterator-seq (.listStatements schema-model))
;;        (map #(.getSubject %))  ;; Properties appear as subjects in schema definitions
;;        (filter #(.isURIResource %)) ;; Keep only resources (ignore blank nodes)
;;        (distinct)))

;; (defn check-undefined-properties
;;   "Checks if all predicates in data-model exist in schema-model."
;;   [schema-model data-model]
;;   (let [data-predicates (set (get-predicates data-model))
;;         schema-properties (set (get-predicates schema-model))
;;         undefined-predicates (set/difference data-predicates schema-properties)]
;;     (if (empty? undefined-predicates)
;;       (println "All predicates in data-model are defined in schema-model.")
;;       (do
;;         (println "The following predicates are missing from the schema-model:")
;;         (doseq [p undefined-predicates] (println (.getURI p)))))))

;; (def shapes-model (load-shapes-model "datashapesorg.ttl"))

;; (def example (jsonld/json-ld-string->model "{\"@context\": \"https://schema.org\", \"@type\": \"Einerfundenertyp\", \"name\": \"Blafoo\", \"geo\": \"nein\"}"))
;; (def example2 (jsonld/json-ld-string->model "{\"@id\": \"http://example.org/foo\", \"http://schema.org/wasganzdummes\": \"baasb\"}"))
;; (def userGraph (RDFDataMgr/loadGraph "user.ttl"))
;; (def validation (validate-data-graph userGraph))
;; (:conforms validation)
;; (def report (:report validation))
;; (println (pr-str (.getEntries report)))

;; (check-undefined-properties shapes-model example)
;; (check-undefined-properties shapes-model example2)

;; (def shapesGraph (RDFDataMgr/loadGraph "datashapesorg.ttl"))


;; (def shapes (Shapes/parse shapesGraph))

;; (def report (.validate (ShaclValidator/get) shapes (.getGraph example2)))

;; (.conforms report)

;; (ShLib/printReport report)

;; (.println System/out)

;; (RDFDataMgr/write System/out (.getModel report) Lang/TTL)


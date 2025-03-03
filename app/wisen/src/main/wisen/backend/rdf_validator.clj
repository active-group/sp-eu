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

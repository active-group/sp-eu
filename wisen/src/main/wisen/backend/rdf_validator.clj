(ns wisen.backend.rdf-validator
  (:require [clojure.java.io]
            [wisen.backend.jsonld :as jsonld])
  (:import (org.apache.jena.sparql.graph GraphFactory)
           (org.apache.jena.riot RDFDataMgr)
           (org.apache.jena.riot Lang)
           (org.apache.jena.shacl ShaclValidator Shapes)))

(defn read-shapes-graph [path]
  (let [in (clojure.java.io/input-stream (clojure.java.io/resource path))
        graph (GraphFactory/createDefaultGraph)]
    (RDFDataMgr/read graph in nil Lang/TTL)
    (Shapes/parse graph)))

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
   (validate-data-graph data-graph (read-shapes-graph  "datashapesorg.ttl"))))

(defn validate-json-ld-string [json-ld-string]
  (-> json-ld-string
      (json-ld-string->graph)
      (validate-data-graph)))

(defn validate-model [model]
  (-> model
      (.getGraph)
      (validate-data-graph)))

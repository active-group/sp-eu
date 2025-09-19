(ns wisen.backend.search
  (:require [wisen.backend.jsonld]
            [wisen.backend.skolem :as skolem]
            [wisen.backend.skolem2 :as skolem2]
            [wisen.common.change-api :as change-api]
            [wisen.backend.osm :as osm]
            [wisen.common.prefix :as prefix]
            [wisen.backend.git :as git]
            [wisen.backend.repository :as repository])
  (:import
   (java.io File)
   (org.eclipse.jgit.api Git AddCommand CommitCommand)
   (org.apache.jena.tdb2 TDB2Factory)
   (org.apache.jena.rdf.model Model ResourceFactory)
   (org.apache.jena.query ARQ ReadWrite QueryExecutionFactory)
   (org.apache.jena.datatypes.xsd XSDDatatype)
   (org.apache.jena.vocabulary SchemaDO)
   (org.apache.jena.sparql.util QueryExecUtils)
   (org.apache.lucene.store Directory FSDirectory)
   (org.apache.jena.riot RDFDataMgr
                         RDFFormat
                         Lang)))

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

(defn run-select-query
  "Run a SPARQL SELECT query, returning a list of maps associating
  variable names with resources"
  [model q]
  (let [qexec (QueryExecutionFactory/create q model)
        results (.execSelect qexec)]
    (gather-results '() results)))

(defn run-construct-query
  "Run a SPARQL SELECT query"
  [model q]
  (let [qexec (QueryExecutionFactory/create q model)
        graph (.execConstruct qexec)]
    graph))

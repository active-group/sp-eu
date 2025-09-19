(ns wisen.backend.triple-store
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

(defn- with-read-model!
  ([f]
   (throw (RuntimeException. "not support any more")))
  ([dataset f]
   (.begin dataset ReadWrite/READ)
   (let [model (.getDefaultModel dataset)]
     (try
       (let [result (f model)]
         (.end dataset)
         result)
       (catch Exception e
         (.abort dataset)
         (throw e))))))

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

(defn decorate-geo! [^Model base-model]
  ;; 1. search for all addresses without longitude/latitude
  (let [results (run-select-query!
                 base-model
                 "SELECT ?address ?country ?locality ?postcode ?street
                   WHERE {
                     ?address <http://schema.org/postalCode> ?postcode .
                     ?address <http://schema.org/streetAddress> ?street .
                     ?address <http://schema.org/addressLocality> ?locality .
                     OPTIONAL { ?address <http://schema.org/addressCountry> ?country . }

                     FILTER NOT EXISTS { ?address <http://www.w3.org/2003/01/geo/wgs84_pos#long> ?long . }
}")]

    ;; 2. for all results:
    ;;    fetch geo coordinates from OSM/Nominatim
    (doseq [result results]
      (let [address (get result "address")
            postcode (get result "postcode")
            street (get result "street")
            locality (get result "locality")
            country (get result "country")

            osm-result (osm/search! (osm/address
                                     osm/address-country country
                                     osm/address-locality locality
                                     osm/address-postcode postcode
                                     osm/address-street street))]

        (if (osm/search-success? osm-result)
          ;; 3. write back geo triples
          (let [lat (osm/search-success-latitude osm-result)
                long (osm/search-success-longitude osm-result)]

            ;; geo has longitude
            (.add base-model
                  address
                  (.createProperty base-model "http://www.w3.org/2003/01/geo/wgs84_pos#long")
                  (.createTypedLiteral base-model long XSDDatatype/XSDdecimal))

            ;; geo has latitude
            (.add base-model
                  address
                  (.createProperty base-model "http://www.w3.org/2003/01/geo/wgs84_pos#lat")
                  (.createTypedLiteral base-model lat XSDDatatype/XSDdecimal)))

          ;; TODO: what to do on error decorating? should the entire transaction fail?
          ::TODO
          )))))

;; -----

;; mapping [repo-uri revision-id] to jena models
(def ^:private models (atom {}))

(defn run-select-query-2!
  "Run a SPARQL SELECT query, returning a list of maps associating
  variable names with resources"

  ([repo-uri q]
   (run-select-query-2! repo-uri (repository/head! repo-uri) q))

  ([repo-uri revision-id q]
   (let [key [repo-uri revision-id]
         model (or (get @models key)
                   (let [model (repository/read! repo-uri revision-id)]
                     (swap! models assoc key model)
                     model))]
     (run-select-query! model q))))

(defn run-construct-query-2!

  ([repo-uri q]
   (run-construct-query-2! repo-uri (repository/head! repo-uri) q))

  ([repo-uri revision-id q]
   (let [key [repo-uri revision-id]
         model (or (get @models key)
                   (let [model (repository/read! repo-uri revision-id)]
                     (swap! models assoc key model)
                     model))]
     (run-construct-query! model q))))

(ns wisen.backend.triple-store
  (:require [wisen.backend.jsonld]
            [wisen.backend.skolem :as skolem]
            [wisen.backend.skolem2 :as skolem2]
            [wisen.common.change-api :as change-api]
            [wisen.backend.osm :as osm]
            [wisen.common.prefix :as prefix])
  (:import
   (java.io File)
   (org.apache.jena.tdb2 TDB2Factory)
   (org.apache.jena.rdf.model Model ResourceFactory)
   (org.apache.jena.query ARQ ReadWrite QueryExecutionFactory)
   (org.apache.jena.datatypes.xsd XSDDatatype)
   (org.apache.jena.vocabulary SchemaDO)
   (org.apache.jena.sparql.util QueryExecUtils)
   (org.apache.lucene.store Directory FSDirectory)))

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
       (.end dataset)
       (catch Exception e
         (.abort dataset)
         (throw e))))))

(defn- with-read-model!
  ([f]
   (with-read-model! @dataset f))
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

(defn add-model!
  ([model-to-add]
   (with-write-model!
     (fn [base-model]
       (add-model! base-model model-to-add))))
  ([base-model model-to-add]
   (let [skolemized-model (skolem/skolemize-model model-to-add "foobar")]
     (mapv (fn [statement]
             (.add base-model statement))
           (iterator-seq (.listStatements skolemized-model))))))

(defn- unwrap! [model obj]
  (cond
    (change-api/literal-string? obj)
    (.createLiteral model (change-api/literal-string-value obj))

    (change-api/literal-decimal? obj)
    (.createTypedLiteral model (change-api/literal-decimal-value obj) XSDDatatype/XSDdecimal)

    (change-api/literal-boolean? obj)
    (.createTypedLiteral model (change-api/literal-boolean-value obj) XSDDatatype/XSDboolean)

    (change-api/literal-time? obj)
    (.createTypedLiteral model (change-api/literal-time-value obj) XSDDatatype/XSDtime)

    (change-api/literal-date? obj)
    (.createTypedLiteral model (change-api/literal-date-value obj) XSDDatatype/XSDdate)

    (change-api/uri? obj)
    (.createResource model (change-api/uri-value obj))))

(defn add-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (unwrap! model obj)]
    (.add ^Model model s p o)))

(defn remove-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (unwrap! model obj)]
    (.remove ^Model model s p o)))

(defn edit-model!
  ([changeset]
   (with-write-model!
     (fn [base-model]
       (edit-model! base-model changeset))))
  ([base-model changeset]
   (let [changes (skolem2/skolemize-changeset changeset)]
     (loop [changes* changes]
       (if (empty? changes*)
         nil
         (let [change (first changes*)]
           (cond
             (change-api/add? change)
             (add-statement! base-model (change-api/add-statement change))

             (change-api/delete? change)
             (remove-statement! base-model (change-api/delete-statement change)))
           (recur (rest changes*))))))))

#_(run-select-query!
 "SELECT ?place ?country ?locality ?postcode ?street
  WHERE {
    ?place a <http://schema.org/Place> .
    ?place <http://schema.org/address> ?address .
    ?address <http://schema.org/postalCode> ?postcode .
    ?address <http://schema.org/streetAddress> ?street .
    ?address <http://schema.org/addressLocality> ?locality .
    ?address <http://schema.org/addressCountry> ?country .

    FILTER NOT EXISTS { ?place <http://schema.org/geo> ?geo . }
}")

(defn decorate-geo!
  ([]
   (with-write-model!
     (fn [base-model]
       (decorate-geo! base-model))))
  ([^Model base-model]
   ;; 1. search for all addresses without longitude/latitude
   (let [results (run-select-query!
                  base-model
                  "SELECT ?address ?country ?locality ?postcode ?street
                   WHERE {
                     ?address <http://schema.org/postalCode> ?postcode .
                     ?address <http://schema.org/streetAddress> ?street .
                     ?address <http://schema.org/addressLocality> ?locality .
                     ?address <http://schema.org/addressCountry> ?country .

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
           ))))))

#_(edit-model! [(change-api/delete change-api/delete-statement
                                 (change-api/statement change-api/statement-subject "http://subject.com"
                                                       change-api/statement-predicate "http://predicate.com"
                                                       change-api/statement-object (change-api/make-literal-string "http://object3.com")))
              (change-api/add change-api/add-statement
                              (change-api/statement change-api/statement-subject "http://subject.com"
                                                    change-api/statement-predicate "http://predicate.com"
                                                    change-api/statement-object (change-api/make-literal-string "http://object4.com")))])

(defn- populate! [model]
  (let [mdl (wisen.backend.jsonld/json-ld-string->model
             "{\"@id\": \"http://example.org/hirsch\",
     \"@type\": \"http://schema.org/Organization\",
     \"http://schema.org/name\": \"Begegnungsstätte Hirsch e.V.\",
     \"http://schema.org/description\": \"Wir sind ein Verein von aktiven älteren und jüngeren Menschen, die dieses Haus seit 1982 betreiben, mit Hilfe weniger bezahlter Mitarbeiter*innen. Neben diesen Hauptamtlichen bringen sich etwa 100 Ehrenamtliche in den unterschiedlichsten Bereichen ein – im Vorstand, in der Cafeteria, als Kurs- und Gruppenleiter oder als Referenten. Die Zielgruppe sind vorwiegend ältere Menschen, aber jeder ist willkommen. Angebote gibt es im Bereich der Erwachsenenbildung: Vorträge, Diskussionsrunden zu aktuellen Themen, Sprachkurse, Gesprächskreise sowie Kurse im kreativen, sportlichen und Freizeitbereich. Die Cafeteria mit ca. 80 Plätzen bietet Möglichkeiten für informelle Kontakte, öffentliche PC-Nutzung und Internetzugang, Spiel und Gespräche bei Bewirtung.\",
     \"http://schema.org/keywords\": \"education, fun, play\",
     \"http://schema.org/areaServed\":
        {\"@type\": \"http://schema.org/GeoCircle\",
         \"@id\": \"http://example.org/hirschAreaServed\",
         \"http://schema.org/geoMidpoint\": {\"@type\": \"http://schema.org/GeoCoordinates\",
                                             \"@id\": \"http://example.org/hirschGeoCoordinates\",
                                             \"http://schema.org/latitude\": \"48\", \"http://schema.org/longitude\": \"9\"}}}")]
    (add-model! model mdl)))

(def dbname "devdb")

(defn setup! []
  ;; Use POST for federated (SERVICE) queries
  (.set (ARQ/getContext) ARQ/httpServiceSendMode "POST")
  (swap! dataset
         (fn [ds]
           (if ds
             ds
             (TDB2Factory/connectDataset dbname)))))

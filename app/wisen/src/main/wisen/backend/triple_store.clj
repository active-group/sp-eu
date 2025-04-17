(ns wisen.backend.triple-store
  (:require [wisen.backend.jsonld]
            [wisen.backend.skolem :as skolem]
            [wisen.common.change-api :as change-api]
            [wisen.backend.osm :as osm])
  (:import
   (org.apache.jena.tdb2 TDB2 TDB2Factory)
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)
   (org.apache.jena.query ARQ ReadWrite QueryExecutionFactory)
   (org.apache.jena.datatypes.xsd XSDDatatype)))

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
   (.add base-model (skolem/skolemize-model model-to-add "foobar"))))

(defn add-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (cond
            (change-api/literal-string? obj)
            (.createLiteral model (change-api/literal-string-value obj))

            (change-api/literal-decimal? obj)
            (.createTypedLiteral model (change-api/literal-decimal-value obj) XSDDatatype/XSDdecimal)

            (change-api/uri? obj)
            (.createResource model (change-api/uri-value obj)))]
    (.add ^Model model s p o)))

(defn remove-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (cond
            (change-api/literal-string? obj)
            (.createLiteral model (change-api/literal-string-value obj))

            (change-api/literal-decimal? obj)
            (.createTypedLiteral model (change-api/literal-decimal-value obj) XSDDatatype/XSDdecimal)

            (change-api/uri? obj)
            (.createResource model (change-api/uri-value obj)))]
    (.remove ^Model model s p o)))

(defn edit-model!
  ([changes]
   (with-write-model!
     (fn [base-model]
       (edit-model! base-model changes))))
  ([base-model changes]
   (let [additions (filter change-api/add? changes)
         deletions (filter change-api/delete? changes)]

     (doall
      (for [addition additions]
        (add-statement! base-model (change-api/add-statement addition))))

     (doall
      (for [deletion deletions]
        (remove-statement! base-model (change-api/delete-statement deletion)))))))

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
   ;; 1. search for all schema.org/Place resources with
   ;; schema.org/address but without schema.org/geo
   (let [results (run-select-query!
                  base-model
                  "SELECT ?place ?country ?locality ?postcode ?street
                   WHERE {
                     ?place a <http://schema.org/Place> .
                     ?place <http://schema.org/address> ?address .
                     ?address <http://schema.org/postalCode> ?postcode .
                     ?address <http://schema.org/streetAddress> ?street .
                     ?address <http://schema.org/addressLocality> ?locality .
                     ?address <http://schema.org/addressCountry> ?country .

                     FILTER NOT EXISTS { ?place <http://schema.org/geo> ?geo . }
}")]

     ;; 2. for all results:
     ;;    fetch geo coordinates from OSM/Nominatim
     (doseq [result results]
       (let [place (get result "place")
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
                 long (osm/search-success-longitude osm-result)
                 geo-uri (str "http://TODO.org/" (random-uuid))
                 geo (.createResource base-model geo-uri)]

             ;; place has geo
             (.add base-model
                   #_(.createResource base-model place-uri)
                   place
                   (.createProperty base-model "http://schema.org/geo")
                   geo)

             ;; geo has longitude
             (.add base-model
                   geo
                   (.createProperty base-model "http://schema.org/longitude")
                   (.createTypedLiteral base-model long XSDDatatype/XSDdecimal))

             ;; geo has latitude
             (.add base-model
                   geo
                   (.createProperty base-model "http://schema.org/latitude")
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
             (let [new-ds (TDB2Factory/connectDataset dbname)]
               (with-write-model! new-ds populate!)
               new-ds)))))


#_(run-select-query!
 "SELECT ?x WHERE { ?x <https://schema.org/name> \"Stadtseniorenrat Tübingen e.V.\"}")

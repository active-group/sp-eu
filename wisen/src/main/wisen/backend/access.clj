(ns wisen.backend.access
  (:require
   [active.data.record :as record :refer [def-record]]
   [active.clojure.logger.event :as event-logger]
   [wisen.common.query :as query]
   [wisen.backend.index :as index]
   [wisen.backend.repository :as repository]
   [wisen.backend.decorator :as decorator]
   [wisen.backend.search :as search]
   [clojure.string :as string]))

(defn- direct-index-records [model]
  (let [res
        (search/run-select-query
         model
         "PREFIX schema: <http://schema.org/>
          PREFIX wgs84: <http://www.w3.org/2003/01/geo/wgs84_pos#>
          SELECT ?id ?lon ?lat ?name ?description
          WHERE {
            ?id schema:name ?name .
            OPTIONAL { ?id schema:description ?description . }
            ?id (schema:location? / schema:address? / schema:geo?) ?geo .
            ?geo (schema:longitude | wgs84:long)  ?lon .
            ?geo (schema:latitue | wgs84:lat) ?lat .
          }")]
    (map (fn [row]
           (index/make-index-record
            (.getURI (get row "id"))
            (.getDouble (get row "lon"))
            (.getDouble (get row "lat"))
            (str (get row "name"))
            (str (get row "description"))))
         res)))

(defn- via-event-index-records [model]
  (let [res
        (search/run-select-query
         model
         "PREFIX schema: <http://schema.org/>
          PREFIX wgs84: <http://www.w3.org/2003/01/geo/wgs84_pos#>
          SELECT ?id ?lon ?lat ?name ?description
          WHERE {
            ?id schema:name ?name .
            OPTIONAL { ?id schema:description ?description . }
            ?parent (schema:event | schema:events) ?id .
            ?parent (schema:location? / schema:address? / schema:geo?) ?geo .
            ?geo (schema:longitude | wgs84:long)  ?lon .
            ?geo (schema:latitue | wgs84:lat) ?lat .
          }")]
    (map (fn [row]
           (index/make-index-record
            (.getURI (get row "id"))
            (.getDouble (get row "lon"))
            (.getDouble (get row "lat"))
            (str (get row "name"))
            (str (get row "description"))))
         res)))

(defn- via-contact-point-index-records [model]
  (let [res
        (search/run-select-query
         model
         "PREFIX schema: <http://schema.org/>
          PREFIX wgs84: <http://www.w3.org/2003/01/geo/wgs84_pos#>
          SELECT ?id ?lon ?lat ?name ?description
          WHERE {
            ?id schema:name ?name .
            OPTIONAL { ?id schema:description ?description . }
            ?parent schema:contactPoint ?id .
            ?parent (schema:location? / schema:address? / schema:geo?) ?geo .
            ?geo (schema:longitude | wgs84:long)  ?lon .
            ?geo (schema:latitue | wgs84:lat) ?lat .
          }")]
    (map (fn [row]
           (index/make-index-record
            (.getURI (get row "id"))
            (.getDouble (get row "lon"))
            (.getDouble (get row "lat"))
            (str (get row "name"))
            (str (get row "description"))))
         res)))

(defn- model->index [model]
  (index/new-in-memory-index
   (concat
    (direct-index-records model)
    (via-event-index-records model)
    (via-contact-point-index-records model))))

(defn head! [prefix repo-uri]
  (repository/head! prefix repo-uri))

(def-record search-result
  [search-result-model
   search-result-relevance
   search-result-total-hits])

(defn search! [repo-uri commit-id query range]
  (let [
        base-model (repository/read! repo-uri commit-id)
        index (model->index base-model)

        [start-index cnt] range
        bb (query/query-geo-bounding-box query)
        min-lat (query/geo-bounding-box-min-lat bb)
        max-lat (query/geo-bounding-box-max-lat bb)
        min-lon (query/geo-bounding-box-min-lon bb)
        max-lon (query/geo-bounding-box-max-lon bb)

        index-bb (index/make-bounding-box min-lat max-lat min-lon max-lon)

        result (if (query/everything-query? query)
                 (index/search-geo index index-bb [start-index cnt])
                 (index/search-text-and-geo
                  index
                  (query/query-fuzzy-search-term query)
                  index-bb
                  [start-index cnt]))

        total-hits (index/search-result-total-hits result)

        uris (index/search-result-uris result)

        _ (event-logger/log-event! :info (str "URIs found in index: " (pr-str uris)))

        uris-string (string/join "," (map (fn [uri]
                                            (str "<" uri ">"))
                                          uris))

        sparql (str "CONSTRUCT { ?s ?p ?o . ?o ?p2 ?o2 . ?o2 ?p3 ?o3 . }
                     WHERE { ?s ?p ?o .
                             OPTIONAL { ?o ?p2 ?o2 . OPTIONAL { ?o2 ?p3 ?o3 . } }
                             FILTER (?s IN ( " uris-string " )) }")

        _ (event-logger/log-event! :info (str "Running sparql: " (pr-str sparql)))

        result-model (search/run-construct-query base-model sparql)]

    (search-result
     search-result-model result-model
     search-result-relevance uris
     search-result-total-hits total-hits)))

(defn change! [repo-uri commit-id changeset]
  (repository/change! repo-uri commit-id changeset "Change"))

(defn resource-description! [repo-uri commit-id resource-uri]
  (let [q (str
           "CONSTRUCT {<"
           resource-uri
           "> ?p ?o .}
          WHERE { <"
           resource-uri
           "> ?p ?o . }")

        model (repository/read! repo-uri commit-id)

        base-model
        (search/run-construct-query model q)

        add-q (str
               "CONSTRUCT {<"
               resource-uri
               "> ?p1 ?o1 . ?o1 ?p2 ?o2 . }
          WHERE { <"
               resource-uri
               "> ?p1 ?o1 . ?o1 ?p2 ?o2 . }"
               "LIMIT 30")

        additional-model
        (search/run-construct-query model add-q)

        result-model (.add base-model additional-model)]

    result-model))

(def-record reference
  [reference-uri
   reference-name])

(defn references! [repo-uri commit-id resource-uri]
  (let [model (repository/read! repo-uri commit-id)
        q (str "SELECT DISTINCT ?reference ?name WHERE { ?reference ?p <"
               resource-uri "> . OPTIONAL { ?reference <http://schema.org/name> ?name . } }")
        result (search/run-select-query model q)]
    (map (fn [line]
           (reference
            reference-uri (.toString (get line "reference"))
            reference-name (when-let [name (get line "name")]
                             (.toString name))))
         result)))

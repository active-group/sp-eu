(ns wisen.backend.index
  (:require [wisen.backend.lucene :as lucene]
            [wisen.backend.embedding :as embedding]
            [wisen.backend.triple-store :as triple-store]
            ))

(defn- prepare-for-retrieval [name description]
  (str name "\n" description))

(defn- prepare-for-query [text]
  (str text))

(defn insert! [id lon lat name description & [dir]]
  (lucene/insert!
   (lucene/id-geo-vec
    lucene/id-geo-vec-id id
    lucene/id-geo-vec-geo (lucene/make-point lon lat)
    lucene/id-geo-vec-vec (lucene/make-vector
                           (embedding/get-embedding
                            (prepare-for-retrieval name description))))
   dir))

(defn- insert-direct! [dir]
  (let [res
        (triple-store/run-select-query!
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
    (doall
     (map (fn [row]
            (insert! (.getURI (get row "id"))
                     (.getDouble (get row "lon"))
                     (.getDouble (get row "lat"))
                     (get row "name")
                     (get row "description")
                     dir))
          res))))

(defn- insert-via-event! [dir]
  (let [res
        (triple-store/run-select-query!
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
    (doall
     (map (fn [row]
            (insert! (.getURI (get row "id"))
                     (.getDouble (get row "lon"))
                     (.getDouble (get row "lat"))
                     (get row "name")
                     (get row "description")
                     dir))
          res))))

(defn- insert-via-contactPoint! [dir]
  (let [res
        (triple-store/run-select-query!
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
    (doall
     (map (fn [row]
            (insert! (.getURI (get row "id"))
                     (.getDouble (get row "lon"))
                     (.getDouble (get row "lat"))
                     (get row "name")
                     (get row "description")
                     dir))
          res))))

(defn update-search-index! [& [dir]]
  (lucene/clear! dir)
  (insert-direct! dir)
  (insert-via-event! dir)
  (insert-via-contactPoint! dir))

(defn search! [text box & [dir]]
  (lucene/search! (lucene/make-vector
                   (embedding/get-embedding
                    (prepare-for-query text)))
                  box
                  dir))

(def file-system-index lucene/file-system-directory)

(defn make-in-memory-index []
  (lucene/make-in-memory-directory))

(defn make-vector [v]
  (lucene/make-vector v))

(defn make-bounding-box [min-lat max-lat min-lon max-lon]
  (lucene/make-bounding-box min-lat max-lat min-lon max-lon))

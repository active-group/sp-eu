(ns wisen.backend.index
  (:require [wisen.backend.lucene :as lucene]
            [wisen.backend.embedding :as embedding]
            [wisen.backend.triple-store :as triple-store]
            ))

(defn- prepare-for-retrieval [name description]
  (str name "\n" description))

(defn- prepare-for-query [text]
  (str text))

(defn get-id-geo-vecs! []
  (let [res
        (triple-store/run-select-query!
         "PREFIX schema: <http://schema.org/>
          PREFIX wgs84: <http://www.w3.org/2003/01/geo/wgs84_pos#>
          SELECT ?id ?lon ?lat ?name ?description
          WHERE {
            ?id schema:name ?name .
            ?id schema:description ?description .
            ?id (schema:location? / schema:address? / schema:geo?) ?geo .
            ?geo (schema:longitude | wgs84:long)  ?lon .
            ?geo (schema:latitue | wgs84:lat) ?lat .
          }")]
    (map (fn [row]
           (lucene/id-geo-vec
            lucene/id-geo-vec-id (.getURI (get row "id"))
            lucene/id-geo-vec-geo (lucene/make-point (.getDouble (get row "lon"))
                                                     (.getDouble (get row "lat")))
            lucene/id-geo-vec-vec (lucene/make-vector
                                   (embedding/get-embedding
                                    (prepare-for-retrieval (get row "name")
                                                           (get row "description"))))))
         res)))

(defn update-search-index! []
  (lucene/clear!)
  (doall
   (map lucene/insert!
        (get-id-geo-vecs!))))

(defn search! [text box]
  (lucene/search! (lucene/make-vector
                   (embedding/get-embedding
                    (prepare-for-query text)))
                  box))

(defn make-vector [v]
  (lucene/make-vector v))

(defn make-bounding-box [min-lat max-lat min-lon max-lon]
  (lucene/make-bounding-box min-lat max-lat min-lon max-lon))

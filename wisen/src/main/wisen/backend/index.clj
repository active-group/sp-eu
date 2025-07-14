(ns wisen.backend.index
  (:require [wisen.backend.lucene :as lucene]
            [wisen.backend.embedding :as embedding]
            [wisen.backend.triple-store :as triple-store]
            ))

(defn get-id-geo-vecs! []
  (let [res
        (triple-store/run-select-query!
         "SELECT ?id ?lon ?lat ?name ?description
      WHERE {
       ?id <http://schema.org/name> ?name .
       ?id <http://schema.org/description> ?description .
       ?id <http://schema.org/location> ?loc .
       ?loc <http://schema.org/geo> ?geo .
       ?geo <http://schema.org/longitude> ?lon .
       ?geo <http://schema.org/latitude> ?lat .
      }")]
    (map (fn [row]
           (lucene/id-geo-vec
            lucene/id-geo-vec-id (.getURI (get row "id"))
            lucene/id-geo-vec-geo (lucene/make-point (.getDouble (get row "lon"))
                                                     (.getDouble (get row "lat")))
            lucene/id-geo-vec-vec (lucene/make-vector
                                   (embedding/get-embedding
                                    (str (get row "name")
                                         "\n\n"
                                         (get row "description"))))))
         res)))

(defn update-search-index! []
  (lucene/clear!)
  (doall
   (map lucene/insert!
        (get-id-geo-vecs!))))

(defn search! [vec box]
  (lucene/search! vec box))

(defn make-vector [v]
  (lucene/make-vector v))

(defn make-bounding-box [min-lat max-lat min-lon max-lon]
  (lucene/make-bounding-box min-lat max-lat min-lon max-lon))

(ns wisen.backend.index
  (:require [wisen.backend.lucene :as lucene]
            [wisen.backend.embedding :as embedding]
            [active.data.record :refer [def-record]]))

(def search-result lucene/search-result)
(def search-result-total-hits lucene/search-result-total-hits)
(def search-result-uris lucene/search-result-uris)

(defn- prepare-for-retrieval [name description]
  (str name "\n" description))

(defn- prepare-for-query [text]
  (str text))

(defn- embedding-vector-for-retrieval [name description]
  (lucene/make-vector
   (embedding/get-embedding
    (prepare-for-retrieval name description))))

(defn- embedding-vector-for-query [text]
  (lucene/make-vector
   (embedding/get-embedding
    (prepare-for-query text))))

;; ---

(def-record index-record
  [index-record-id
   index-record-longitude
   index-record-latitude
   index-record-name
   index-record-description])

(defn make-index-record [id lon lat name description]
  (index-record
   index-record-id id
   index-record-longitude lon
   index-record-latitude lat
   index-record-name name
   index-record-description description))

(defn new-in-memory-index [index-records]
  (let [i (lucene/make-in-memory-directory)]
    (doall (for [irec index-records]
             (lucene/insert!
              i
              (lucene/id-geo-vec
               lucene/id-geo-vec-id (index-record-id irec)
               lucene/id-geo-vec-geo (lucene/make-point (index-record-longitude irec)
                                                        (index-record-latitude irec))
               lucene/id-geo-vec-vec (embedding-vector-for-retrieval (index-record-name irec)
                                                                     (index-record-description irec))))))
    i))

(def search-result lucene/search-result)
(def search-result-total-hits lucene/search-result-total-hits)
(def search-result-uris lucene/search-result-uris)

(defn make-geo-query [box]
  (lucene/geo-query box))

(defn make-fuzzy-text-query [text]
  (lucene/knn-query
   (embedding-vector-for-query text)))

(defn combine-queries [q1 q2]
  (lucene/combine-queries q1 q2))

(defn make-bounding-box [min-lat max-lat min-lon max-lon]
  (lucene/make-bounding-box min-lat max-lat min-lon max-lon))

(defn search-geo
  "Returns a `search-result`"
  [index box range]
  (lucene/run-query!
   index
   (make-geo-query box)
   range))

(defn search-text-and-geo
  "Returns a `search-result`"
  [index text box range]
  (lucene/run-query!
   index
   (combine-queries
    (make-geo-query box)
    (make-fuzzy-text-query text))
   range))

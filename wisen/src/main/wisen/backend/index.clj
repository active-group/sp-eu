(ns wisen.backend.index
  (:require [wisen.backend.lucene :as lucene]
            [wisen.backend.embedding :as embedding]
            [active.clojure.logger.event :refer [log-event! log-msg]]
            [active.data.record :refer [def-record]]
            [active.data.realm :as realm]))

(def search-result lucene/search-result)
(def search-result-total-hits lucene/search-result-total-hits)
(def search-result-uris lucene/search-result-uris)

(defn- prepare-for-retrieval [name description keywords]
  (str name "\n" description "\n\nKeywords: " keywords))

(defn- prepare-for-query [text]
  (str text))

(defn- embedding-vector-for-retrieval [name description keywords]
  (lucene/make-vector
   (embedding/get-embedding
    (prepare-for-retrieval name description keywords))))

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
   index-record-description
   index-record-keywords :- realm/string])

(defn make-index-record [id lon lat name description keywords]
  (index-record
   index-record-id id
   index-record-longitude lon
   index-record-latitude lat
   index-record-name name
   index-record-description description
   index-record-keywords keywords))

(defn new-in-memory-index [index-records]
  (log-event! :info (log-msg "Creating new in-memory-index ..."))
  (let [i (lucene/make-in-memory-directory)]
    (doall (for [irec index-records]
             (lucene/insert!
              i
              (lucene/id-geo-vec
               lucene/id-geo-vec-id (index-record-id irec)
               lucene/id-geo-vec-geo (lucene/make-point (index-record-longitude irec)
                                                        (index-record-latitude irec))
               lucene/id-geo-vec-vec (embedding-vector-for-retrieval (index-record-name irec)
                                                                     (index-record-description irec)
                                                                     (index-record-keywords irec))))))
    (log-event! :info (log-msg "... done creating new in-memory-index"))
    i))

(def search-result lucene/search-result)
(def search-result-total-hits lucene/search-result-total-hits)
(def search-result-uris lucene/search-result-uris)

(defn make-geo-query [box]
  (lucene/geo-query box))

(defn fuzzy-text-in-box-query [text box]
  (lucene/knn-float-vector-query
   (embedding-vector-for-query text)
   box))

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
  [
   index ; the index object
   text  ; the fuzzy search term
   box   ; the geo area
   range ; for pagination
   ]

  (lucene/run-query!
   index
   (fuzzy-text-in-box-query text box)
   range))

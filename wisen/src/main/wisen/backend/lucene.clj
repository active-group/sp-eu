(ns wisen.backend.lucene
  (:require [active.data.record :refer [def-record]]
            [active.data.realm :as realm])
  (:import
   (java.io File)
   (org.locationtech.spatial4j.context SpatialContext)
   (org.apache.lucene.spatial.prefix RecursivePrefixTreeStrategy)
   (org.apache.lucene.spatial.prefix.tree GeohashPrefixTree)
   (org.apache.lucene.spatial.query SpatialArgs
                                    SpatialOperation)
   (org.apache.lucene.document StoredField)
   (org.apache.lucene.analysis Analyzer)
   (org.apache.lucene.analysis.standard StandardAnalyzer)
   (org.apache.lucene.document Document
                               KnnVectorField
                               StringField
                               Field
                               Field$Store)
   (org.apache.lucene.index DirectoryReader
                            IndexReader
                            IndexWriter
                            IndexWriterConfig)
   (org.apache.lucene.search IndexSearcher
                             KnnVectorQuery
                             Query
                             ScoreDoc
                             TopDocs
                             BooleanQuery$Builder
                             BooleanClause$Occur)
   (org.apache.lucene.store Directory
                            FSDirectory
                            ByteBuffersDirectory)))

(def-record id-geo-vec
  [id-geo-vec-id :- realm/string
   id-geo-vec-geo ;; (make-point ...)
   id-geo-vec-vec ;; (make-vector ...)
   ])

(defn make-vector [v]
  (float-array v))

(def ctx SpatialContext/GEO)

(def grid (GeohashPrefixTree. ctx 11))

(def strategy (RecursivePrefixTreeStrategy. grid "location"))

;; ---

(def file-system-directory
  (FSDirectory/open (.toPath (File. "lucene-test-6"))))

(defn make-in-memory-directory []
  (ByteBuffersDirectory.))

(def analyzer
  (StandardAnalyzer.))

(defn with-writer! [f & [dir]]
  (let [cfg (IndexWriterConfig. analyzer)
        w (IndexWriter. (or dir file-system-directory) cfg)]
    (try
      (f w)
      (finally
        (.close w)))))

(defn make-point [lon lat]
  (.makePoint ctx lon lat))

(defn make-bounding-box [min-lat max-lat min-lon max-lon]
  [[min-lat max-lat] [min-lon max-lon]])

(defn- box->shape [[[min-lat max-lat] [min-lon max-lon]]]
  (.rect (.getShapeFactory ctx)
         min-lon
         max-lon
         min-lat
         max-lat))

(defn insert! [id-geo-vec & [dir]]
  (let [id (id-geo-vec-id id-geo-vec)
        point (id-geo-vec-geo id-geo-vec)
        vec (id-geo-vec-vec id-geo-vec)]
    (with-writer!
      (fn [writer]
        (let [doc (Document.)]
          (.add doc (KnnVectorField. "embedding" vec))
          (.add doc (StringField. "id" id Field$Store/YES))

          (doall
           (map
            (fn [f]
              (.add doc f))
            (.createIndexableFields strategy point)))

          (.add doc (StoredField. (.getFieldName strategy) (pr-str point)))

          (.addDocument writer doc)))
      dir)))

(defn clear! [& [dir]]
  (with-writer!
    (fn [writer]
      (.deleteAll writer))
    dir))

(defn with-searcher! [f & [dir]]
  (let [r (DirectoryReader/open (or dir file-system-directory))
        s (IndexSearcher. r)]
    (try
      (f s)
      (finally
        (.close r)))))

(def desired-number-of-search-results 2)

(defn geo-query [box]
  (let [args (SpatialArgs. SpatialOperation/Intersects (box->shape box))
        geo-query (.makeQuery strategy args)]
    geo-query))

(defn knn-query [vec]
  (KnnVectorQuery. "embedding" vec desired-number-of-search-results))

(defn combine-queries [q1 q2]
  (let [query-builder (BooleanQuery$Builder.)
        _ (.add query-builder q1 BooleanClause$Occur/MUST)
        _ (.add query-builder q2 BooleanClause$Occur/MUST)]
    (.build query-builder)))

(defn run-query! [query & [dir]]
  (with-searcher!
    (fn [searcher]
      (let [topDocs (.search searcher query desired-number-of-search-results)]
        (doall (map (fn [scoreDoc]
                      (let [foundDoc (.doc searcher (.-doc scoreDoc))]
                        (.get foundDoc "id")))
                    (seq (.scoreDocs topDocs))))))
    dir))

(defn search! [vec box & [dir]]
  (run-query! (combine-queries
               (geo-query box)
               (knn-query vec))
              dir))

#_(search! (float-array [2.0 3.2 4.2]) [[0 10] [0 10]])

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

(def-record search-result
  [search-result-total-hits :- realm/integer
   search-result-uris :- (realm/sequence-of realm/string)])

(defn make-vector [v]
  (float-array v))

(def ctx SpatialContext/GEO)

(def grid (GeohashPrefixTree. ctx 11))

(def strategy (RecursivePrefixTreeStrategy. grid "location"))

;; ---

(declare with-writer!)

(defn make-in-memory-directory []
  (let [dir (ByteBuffersDirectory.)]
    ;; We cannot write to an "empty" ByteBuffersDirectory immediately,
    ;; so we create an empty commit
    (with-writer! dir
      (fn [w]
        (.commit w)))
    dir))

(def analyzer
  (StandardAnalyzer.))

(defn with-writer! [dir f]
  (let [cfg (IndexWriterConfig. analyzer)
        w (IndexWriter. dir cfg)]
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

(defn insert! [dir id-geo-vec]
  (let [id (id-geo-vec-id id-geo-vec)
        point (id-geo-vec-geo id-geo-vec)
        vec (id-geo-vec-vec id-geo-vec)]
    (with-writer!
      dir
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

          (.addDocument writer doc))))))

(defn clear! [dir]
  (with-writer!
    dir
    (fn [writer]
      (.deleteAll writer))))

(defn with-searcher! [dir f]
  (let [r (DirectoryReader/open dir)
        s (IndexSearcher. r)]
    (try
      (f s)
      (finally
        (.close r)))))

(def desired-number-of-search-results 25)

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

(defn run-query!
  "Takes query built with `knn-query`, `geo-query`, or
  `combine-queries`. Returns `search-result`."
  [dir query [from cnt]]
  (with-searcher!
    dir
    (fn [searcher]
      (let [topDocs (.search searcher query (+ from cnt))
            total-hits (.-value (.totalHits topDocs))]
        (search-result
         search-result-total-hits total-hits
         search-result-uris (doall (map (fn [scoreDoc]
                                          (let [foundDoc (.doc searcher (.-doc scoreDoc))]
                                            (.get foundDoc "id")))
                                        (take cnt
                                              (drop from
                                                    (seq (.scoreDocs topDocs)))))))))))

#_#_#_(def i (make-in-memory-directory))
(run-query! i
            (geo-query (make-bounding-box 0 10 0 10))
            [0 5])

(DirectoryReader/open i)

#_(search! (float-array [2.0 3.2 4.2]) [[0 10] [0 10]])


#_(with-writer!
  i
  (fn [writer]
    (let [doc (Document.)]
      (.add doc (StringField. "id" "id1" Field$Store/YES))
      (.add doc (StringField. "name" "Marki" Field$Store/YES))

      (.addDocument writer doc))))

(ns wisen.backend.lucene
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
                            FSDirectory)))


(def ctx SpatialContext/GEO)

(def grid (GeohashPrefixTree. ctx 11))

(def strategy (RecursivePrefixTreeStrategy. grid "location"))

;; ---

(def directory
  (FSDirectory/open (.toPath (File. "lucene-test"))))

(def analyzer
  (StandardAnalyzer.))

(defn with-writer! [f]
  (let [cfg (IndexWriterConfig. analyzer)
        w (IndexWriter. directory cfg)]
    (try
      (f w)
      (finally
        (.close w)))))

(defn- location->shape [[lon lat]]
  (.makePoint ctx lon lat))

(defn- box->shape [[[min-lat max-lat] [min-lon max-lon]]]
  (.rect (.getShapeFactory ctx)
         min-lon
         max-lon
         min-lat
         max-lat))

(defn insert! [id vec location]
  (with-writer!
    (fn [writer]
      (def doc (Document.))
      (.add doc (KnnVectorField. "embedding" vec))
      (.add doc (StringField. "id" id Field$Store/YES))

      (doall
       (map
        (fn [f]
          (.add doc f))
        (.createIndexableFields strategy
                                (location->shape location))))

      (.add doc (StoredField. (.getFieldName strategy) (pr-str location)))

      (.addDocument writer doc))))

(insert! "doc1" (float-array [2.3 3.4 4.1]) [1 2])
(insert! "doc2" (float-array [5.3 5.4 8.1]) [3 4])

(defn with-searcher! [f]
  (let [r (DirectoryReader/open directory)
        s (IndexSearcher. r)]
    (try
      (f s)
      (finally
        (.close r)))))

(defn search! [vec box]
  (with-searcher!
    (fn [searcher]
      (let [args (SpatialArgs. SpatialOperation/Intersects (box->shape box))
            geo-query (.makeQuery strategy args)
            knn-query (KnnVectorQuery. "embedding" vec 1)
            query-builder (BooleanQuery$Builder.)
            _ (.add query-builder knn-query BooleanClause$Occur/MUST)
            _ (.add query-builder geo-query BooleanClause$Occur/SHOULD)
            query (.build query-builder)
            topDocs (.search searcher query 1)]

        (doall (map (fn [scoreDoc]
                      (let [foundDoc (.doc searcher (.-doc scoreDoc))]
                        (.get foundDoc "id")))
                    (seq (.scoreDocs topDocs))))))))

#_(search! (float-array [2.0 3.2 4.2]) [[0 10] [0 10]])

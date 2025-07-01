(ns wisen.backend.lucene
  (:import
   (org.apache.lucene.analysis Analyzer)
   (org.apache.lucene.analysis.standard StandardAnalyzer)
   (org.apache.lucene.document Document KnnVectorField StringField Field Field$Store)
   (org.apache.lucene.index DirectoryReader IndexReader IndexWriter IndexWriterConfig)
   (org.apache.lucene.search IndexSearcher KnnVectorQuery Query ScoreDoc TopDocs)
   (org.apache.lucene.store Directory FSDirectory)))

(def directory
  (FSDirectory/open (.toPath (File. "lucene-test-3"))))

(def analyzer
  (StandardAnalyzer.))

(defn with-writer! [f]
  (let [cfg (IndexWriterConfig. analyzer)
        w (IndexWriter. directory cfg)]
    (try
      (f w)
      (finally
        (.close w)))))

(defn insert! [id vec]
  (with-writer!
    (fn [writer]
      (def doc (Document.))
      (.add doc (KnnVectorField. "embedding" vec))
      (.add doc (StringField. "id" id Field$Store/YES))
      (.addDocument writer doc))))

#_(insert! "doc1" (float-array [2.3 3.4 4.1]))
#_(insert! "doc2" (float-array [5.3 5.4 8.1]))

(defn with-searcher! [f]
  (let [r (DirectoryReader/open directory)
        s (IndexSearcher. r)]
    (try
      (f s)
      (finally
        (.close r)))))

(defn search! [vec]
  (with-searcher!
    (fn [searcher]
      (let [knnQuery (KnnVectorQuery. "embedding" vec 1)
            topDocs (.search searcher knnQuery 1)]
        (doall (map (fn [scoreDoc]
                      (let [foundDoc (.doc searcher (.-doc scoreDoc))]
                        (.get foundDoc "id")))
                    (seq (.scoreDocs topDocs))))))))

#_(search! (float-array [9.1 2.3 3.0]))

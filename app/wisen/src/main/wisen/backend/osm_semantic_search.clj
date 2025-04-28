(ns wisen.backend.osm-semantic-search
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.core.cache :as cache])
  (:import #_[ai.djl.repository Repository]
           [ai.djl.repository.zoo Criteria ModelZoo]
           [ai.djl.translate Translator Batchifier]
           [ai.djl.ndarray NDList]
           [ai.djl.huggingface.tokenizers HuggingFaceTokenizer]
           [org.slf4j LoggerFactory]))

;; Cache for embeddings (to avoid re-computing)
(def embedding-cache (atom (cache/ttl-cache-factory {} :ttl (* 24 60 60 1000)))) ;; 24 hours

;; Custom translator for embeddings
(deftype EmbeddingTranslator [tokenizer]
  Translator
  (processInput [_ ctx input]
    (let [manager (.getManager ctx)
          sentence (str input)
          tokenized (.encode tokenizer sentence)
          input-ids (int-array (get tokenized "input_ids"))
          attention-mask (int-array (get tokenized "attention_mask"))]
      (doto (NDList.)
        (.add (.create manager input-ids (long-array [(count input-ids)])))
        (.add (.create manager attention-mask (long-array [(count attention-mask)]))))))

  (processOutput [_ _ctx model-output]
    (let [last-hidden-state (.get model-output 0)
          pooled (.mean last-hidden-state 1)
          normalized (.normalize pooled "l2")]
      (.toFloatArray normalized)))

  (getBatchifier [_] Batchifier/STACK))

;; DJL model + tokenizer
;; TODO: should be configurable
(def model-name "BAAI/bge-m3")

(defn initialize-model []
  (println "Initializing embedding model...")

  (let [cls (Class/forName "ai.djl.repository.zoo.DefaultModelZoo")
        logger-field (.getDeclaredField cls "logger")]
    (.setAccessible logger-field true)
    (println "Current logger value:" (.get logger-field nil)))

  (System/setProperty "ai.djl.huggingface.tokenizers.cache.dir"
                      (str (System/getProperty "user.home") "/.cache/huggingface/hub"))
  (System/setProperty "ai.djl.repository.zoo.location"
                      (str (System/getProperty "user.home") "/.cache/huggingface/hub"))
  (let [model-path (str (System/getProperty "user.home")
                        "/.cache/huggingface/hub/models--BAAI--bge-m3/")
        tokenizer (HuggingFaceTokenizer/newInstance "BAAI/bge-m3")
        translator (new EmbeddingTranslator tokenizer)
        criteria (-> (Criteria/builder)
                     (.setTypes String (Class/forName "[F"))
                     (.optModelPath (java.nio.file.Paths/get (java.net.URI. (str "file://" model-path))))
                     (.optTranslator translator)
                     (.optEngine "PyTorch")
                     (.build))]
    ;; Try with a try-catch to get more detailed error information
    (try
      (let [model (ModelZoo/loadModel criteria)]
        {:model model
         :predictor (.newPredictor model)})
      (catch Exception e
        (println "Error loading model:")
        (.printStackTrace e)
        (throw e)))))

(defonce model-state (atom nil))

(defn ensure-model-loaded []
  (when (nil? @model-state)
    (reset! model-state (initialize-model)))
  @model-state)

(defn shutdown-model []
  (when-let [{:keys [model predictor]} @model-state]
    (.close predictor)
    (.close model)
    (reset! model-state nil)))

;; -- Embedding helpers --

(defn get-embedding [text]
  (if (cache/has? @embedding-cache text)
    (cache/lookup @embedding-cache text)
    (let [{:keys [predictor]} (ensure-model-loaded)
          embedding (.predict predictor text)]
      (swap! embedding-cache cache/miss text embedding)
      embedding)))

(defn batch-embeddings [texts]
  (let [{:keys [predictor]} (ensure-model-loaded)]
    (.batchPredict predictor texts)))

;; -- OSM helpers --

(defn fetch-osm-data [min-lat min-lon max-lat max-lon]
  (let [overpass-url "https://overpass-api.de/api/interpreter"
        query (str "[out:json][timeout:25];"
                   "(node[~\".*\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "way[~\".*\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "relation[~\".*\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   ");"
                   "out body;"
                   ">;")
        response (http/post overpass-url {:form-params {:data query}
                                          :socket-timeout 30000
                                          :conn-timeout 30000})]
    (json/read-str (:body response))))

(defn extract-features [element]
  (let [tags (get element "tags" {})
        fields [(get tags "name")
                (get tags "name:en")
                (get tags "description")
                (get tags "amenity")
                (get tags "shop")
                (get tags "tourism")
                (get tags "leisure")
                (get tags "historic")
                (get tags "cuisine")
                (get element "type")]]
    (str/join " " (remove str/blank? fields))))

(defn cosine-similarity [vec1 vec2]
  (let [dot (reduce + (map * vec1 vec2))
        norm1 (Math/sqrt (reduce + (map #(* % %) vec1)))
        norm2 (Math/sqrt (reduce + (map #(* % %) vec2)))]
    (if (zero? (* norm1 norm2))
      0.0
      (/ dot (* norm1 norm2)))))

;; -- Main semantic search --

(defn rank-osm-results [query-embedding osm-elements]
  (let [elements (map #(assoc % :features (extract-features %))
                      (filter #(seq (get % "tags")) osm-elements))
        features (mapv :features elements)
        embeddings (batch-embeddings features)
        elements-with-embeddings (mapv (fn [element emb]
                                         (assoc element
                                                :embedding emb
                                                :similarity (cosine-similarity query-embedding emb)))
                                       elements
                                       embeddings)
        valid-elements (filter :similarity elements-with-embeddings)]
    (reverse (sort-by :similarity valid-elements))))

(defn format-result [element]
  (let [tags (get element "tags" {})
        id (get element "id")
        type (get element "type")
        name (or (get tags "name") (str type " " id))
        similarity (:similarity element)]
    {:id id
     :type type
     :name name
     :tags tags
     :similarity similarity}))

(defn semantic-osm-search
  "Semantic search over OSM area for a natural language description."
  [description min-lat min-lon max-lat max-lon & {:keys [limit] :or {limit 20}}]
  (println "Semantic search:" description)
  (println "Area:" [min-lat min-lon max-lat max-lon])
  (let [query-embedding (get-embedding description)
        osm-data (fetch-osm-data min-lat min-lon max-lat max-lon)
        elements (get osm-data "elements")
        ranked (rank-osm-results query-embedding elements)
        top-results (take limit ranked)]
    {:query description
     :bbox {:min_lat min-lat :min_lon min-lon :max_lat max-lat :max_lon max-lon}
     :total_matches (count ranked)
     :results (map format-result top-results)}))


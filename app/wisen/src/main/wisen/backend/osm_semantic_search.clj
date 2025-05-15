(ns wisen.backend.osm-semantic-search
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [clojure.core.cache :as cache]
            [ring.util.codec :as codec])
  (:import [ai.djl.repository.zoo Criteria ModelZoo]
           [ai.djl.huggingface.translator TextEmbeddingTranslatorFactory]))

;; Cache for embeddings (to avoid re-computing)
(def embedding-cache (atom (cache/ttl-cache-factory {} :ttl (* 24 60 60 1000)))) ;; 24 hours

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
                        "/.cache/huggingface/hub/models--BAAI--bge-m3/bge-m3.pt")
        criteria (-> (Criteria/builder)
                     (.setTypes String (Class/forName "[F"))
                     (.optModelPath (java.nio.file.Paths/get (java.net.URI. (str "file://" model-path))))
                     (.optTranslatorFactory (TextEmbeddingTranslatorFactory.))
                     (.optArgument "tokenizer", "BAAI/bge-m3")
                     (.optEngine "PyTorch")
                     (.build))
        model (ModelZoo/loadModel criteria)]
    {:model model
     :predictor (.newPredictor model)}))

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

(defn test-embedding []
  (let [embedding (get-embedding "This is a test text")]
    (println "Embedding size:" (count embedding))
    (println "First few values:" (take 5 embedding))
    embedding))

;; -- OSM helpers --

(defn fetch-osm-data [min-lat min-lon max-lat max-lon]
  (let [base-url "https://overpass-api.de/api/interpreter?data="
        query (str "[out:json];"
                   "("
                   "  node[\"leisure\"~\"^(sports_centre|park|fitness_centre|swimming_pool|pitch|playground)$\"]"
                   "    (" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  node[\"sport\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  node[\"club\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  node[\"community_centre\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  node[\"social_facility\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  node[\"amenity\"~\"^(community_centre|social_centre|library|arts_centre|healthcare|wellness)$\"]"
                   "    (" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  way[\"leisure\"~\"^(sports_centre|park|fitness_centre|swimming_pool|pitch|playground)$\"]"
                   "    (" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  way[\"sport\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  way[\"community_centre\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  way[\"social_facility\"](" min-lat "," min-lon "," max-lat "," max-lon ");"
                   "  way[\"amenity\"~\"^(community_centre|social_centre|library|arts_centre|healthcare|wellness)$\"]"
                   "    (" min-lat "," min-lon "," max-lat "," max-lon ");"
                   ");"
                   "out body meta;")
        encoded-query (codec/url-encode query)
        url (str base-url encoded-query)
        response (http/get url {:accept :json
                                :as :json-string-keys})
        elems (get (:body response) "elements")
        _ (println (str "number of elements: " (count elems)))]
     elems))

(defn extract-features [element]
  (let [tags (get element "tags" {})
        extract [ "name" "name:en" "official_name" "short_name"
                  "description" "operator" "brand" "cuisine"
                  "amenity" "shop" "leisure" "tourism"
                  "historic" "religion" "denomination"
                  "sport" "healthcare" "social_facility"
                  "community_centre" "craft" "man_made" "office"
                  "club" "public_transport" "education" ]
        values (->> extract
                    (map #(get tags %))
                    (filter some?)
                    (mapcat #(str/split % #"\s*;\s*")) ;; split semi-colon lists
                    (map str/trim)
                    (remove str/blank?)
                    distinct)]
    (str/join " " values)))

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
        embeddings #_(batch-embeddings features) (mapv get-embedding features)
        elements-with-embeddings (mapv (fn [element emb]
                                         (assoc element
                                                :embedding emb
                                                :similarity (cosine-similarity query-embedding emb)))
                                       elements
                                       embeddings)
        valid-elements (filter :similarity elements-with-embeddings)]
    (reverse (sort-by :similarity valid-elements))))

#_(defn format-result [element]
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
        ranked (rank-osm-results query-embedding osm-data)
        top-results (take limit ranked)]
    {:query description
     :bbox {:min_lat min-lat :min_lon min-lon :max_lat max-lat :max_lon max-lon}
     :total_matches (count ranked)
     :results top-results}))

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

(def osm-tags
  {:general [
    ["leisure" #"^(sports_centre|park|fitness_centre|swimming_pool|pitch|playground)$"]
    ["sport" nil]
    ["community_centre" nil]
    ["social_facility" nil]
    ["amenity" #"^(community_centre|social_centre|library|arts_centre|healthcare|wellness|support_group)$"]
  ]

   :elderly [
    ["target_group" #"elderly|senior|older_people"]
    ["social_facility" #"nursing_home|assisted_living|retirement_home|day_care"]
    ["amenity" #"senior_centre"]
    ["description" #"elderly|senior"]
  ]

   :lgbtq [
    ["target_group" #"lgbt|trans|queer|gay|lesbian"]
    ["community" #"lgbt"]
    ["description" #"lgbt|queer|trans|gay|lesbian"]
    ["name" #"lgbt|queer"]
  ]

   :immigrants [
    ["target_group" #"immigrant|refugee|migrant"]
    ["community" #"immigrant|refugee|multicultural|intercultural"]
    ["description" #"language|immigrant|integration|refugee|german_course"]
    ["amenity" #"language_school|adult_education"]
  ]

   :mental_health [
    ["healthcare" #"mental_health|psychotherapy"]
    ["amenity" #"counselling|support_group"]
    ["social_facility" #"therapy|day_care"]
    ["description" #"mental|therapy|stress|wellbeing"]
  ]

   :culture_creative [
    ["amenity" #"arts_centre|library|museum"]
    ["leisure" #"music_venue|theatre"]
    ["description" #"art|creative|cultural|craft|music"]
  ]

   :volunteering [
    ["community" #"volunteer|mutual_aid|charity"]
    ["description" #"volunteer|helping|supporting|donation"]
  ]

   :spiritual [
    ["amenity" #"place_of_worship"]
    ["description" #"spiritual|faith|religious|mosque|church|temple|synagogue"]
  ]

   :youth [
    ["leisure" #"youth_centre|club"]
    ["amenity" #"youth_centre"]
    ["target_group" #"youth|teen|young"]
    ["description" #"youth|teen|young"]
  ]})

(defn tag->query [[k v] min-lat min-lon max-lat max-lon]
  (let [key (str "\"" k "\"")
        match (if v
                (str "~\"" v "\"")
                "")
        bbox (str "(" min-lat "," min-lon "," max-lat "," max-lon ");")]
    (str
      "  node[" key match "]" bbox
      "  way[" key match "]" bbox)))

(defn fetch-osm-data
  [min-lat min-lon max-lat max-lon]
  (let [all-tags (apply concat (vals osm-tags))
        tag-queries (map #(tag->query % min-lat min-lon max-lat max-lon) all-tags)
        query (str "[out:json];(" (str/join "\n" tag-queries) ");out body geom qt;")
        response (http/post "https://overpass-api.de/api/interpreter"
                    {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                     :form-params {"data" query}
                     :accept :json
                     :as :json-string-keys})
        elems (get (:body response) "elements")]
    (println (str "Fetched " (count elems) " OSM elements"))
    elems))

(defn assign-centroid [element]
  (if (and (not (contains? element "lat"))
           (contains? element "geometry"))
    (let [coords (get element "geometry")
          count (count coords)
          sum-lat (reduce + (map #(get % "lat") coords))
          sum-lon (reduce + (map #(get % "lon") coords))]
      (assoc element
             "lat" (/ sum-lat count)
             "lon" (/ sum-lon count)))
    element))

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
     :results (map assign-centroid top-results)}))

(ns wisen.backend.embedding
  (:require [clojure.core.cache :as cache])
  (:import [ai.djl.huggingface.tokenizers HuggingFaceTokenizer]
           ;; [ai.djl.huggingface.translator TextEmbeddingTranslatorFactory]
           [ai.djl.ndarray NDList]
           [ai.djl.repository.zoo Criteria ModelZoo]
           [ai.djl.translate Translator]
           [java.nio.file Paths]))

;; Cache for embeddings (to avoid re-computing)
(def embedding-cache (atom (cache/ttl-cache-factory {} :ttl (* 24 60 60 1000)))) ;; 24 hours

(def model-name (System/getenv "TS_MODEL_NAME"))

(def model-dir (System/getenv "TS_MODEL_DIR"))

(def model-path (str model-dir "/model.pt"))

(def tokenizer-path (str model-dir "/tokenizer"))

(defn normalize-vector [v]
  (let [norm-squared (reduce + (map (fn [x] (* x x)) v))
        norm (Math/sqrt norm-squared)]
    (if (> norm 0.0)
      (map #(/ % norm) v)
      v)))


(defn custom-translator [tokenizer-path]
  (let [tokenizer (HuggingFaceTokenizer/newInstance (Paths/get tokenizer-path (make-array String 0)))]
    (reify Translator
      (processInput [_ ctx input]
        (let [encoding (.encode tokenizer input)
              manager (.getNDManager ctx)
              input-ids (.getIds encoding)
              attention-mask (.getAttentionMask encoding)
              input-array (.create manager (int-array input-ids))
              attention-array (.create manager (int-array attention-mask))
              input-batched (.expandDims input-array 0)
              attention-batched (.expandDims attention-array 0)]
          (NDList. (into-array [input-batched attention-batched]))))

      (processOutput [_ _ctx output]
        (let [embedding (.head output)
              pooled (-> embedding
                        ;; Mean pooling across token dimension (dimension 1)
                        (.mean (int-array [1]))
                        (.toFloatArray))
              ;; L2 normalize the embedding (important for cosine similarity)
              normalized (normalize-vector pooled)]
          normalized))

      (getBatchifier [_] nil))))

(defn initialize-model []
  (println "Initializing embedding model:" model-name)
  (println "Model path:" model-path)
  (println "Tokenizer path:" tokenizer-path)

  (let [translator (custom-translator tokenizer-path)
        criteria (-> (Criteria/builder)
                     (.setTypes String (Class/forName "[F"))
                     (.optModelPath (Paths/get (java.net.URI. (str "file://" model-path))))
                     ;; Somehow, using the built .optTranslatorFactory and providing the local tokenizer file
                     ;; does not work, so we use a custom-translator here.
                     (.optTranslator translator)
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

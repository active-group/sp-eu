(ns wisen.backend.llm
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [jsonista.core :as jsonista]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.skolem :as skolem]
            [wisen.backend.rdf-validator :as validator])
  (:import (com.fasterxml.jackson.core JsonFactory JsonParser$Feature)
           (com.fasterxml.jackson.databind ObjectMapper)))

(defn flatten-multiline-string [s]
  (->> s
       (clojure.string/split-lines)
       (map clojure.string/trim)
       (remove empty?)
       (clojure.string/join " ")
       (clojure.string/trim)))

(def ensurances
  "1. The output strictly follows Schema.org definitions. 2. All provided
  information is included. 3. No additional information is added. 4. The output
  is valid JSON-LD. 5. The response contains only the JSON-LD, with no
  explanations, headers, or formatting outside the JSON structure.")

(def guidelines
  "Where applicable, stick to the following guide lines:
    - for recurring events use `schema:Schedule`
    - for addresses use `schema:PostalAddress`
    - for `schema:startTime` or `schema:endTime` use a `xsd:time` or a `xsd:dateTime`.")

(defn format-ollama-prompt [prompt]
  (flatten-multiline-string
   (format
    "Convert the following natural language description into JSON-LD using only
    Schema.org vocabulary. Ensure that: %s %s Input: %s Output: "
    ensurances
    guidelines
    prompt)))

(defn make-ollama-request-string [prompt]
  (str "{\"model\": \"phi4\", \"stream\": false, \"prompt\": \"" prompt "\"}"))

(defn extract-json-ld [s]
  (-> s
      (str/replace #"^```json\n|```json-ld\n" "")
      (str/replace #"\n```$" "")))

(def remove-comments-mapper
  (let [factory (doto (JsonFactory.) (.enable JsonParser$Feature/ALLOW_COMMENTS))]
    (ObjectMapper. factory)))

(defn remove-comments-from-json-string [json-str]
  (jsonista/write-value-as-string (.readValue remove-comments-mapper json-str Object)))

(defn prepare-llm-response [json-ld-string]
  (-> json-ld-string
      (extract-json-ld)
      (remove-comments-from-json-string)))

(defn ollama-request! [prompt]
  (let [response
        (client/post "http://localhost:11434/api/generate" {:content-type :json
                                                            :accept :json
                                                            :as :json
                                                            :body (make-ollama-request-string
                                                                   (format-ollama-prompt prompt))})]
    (case (:status response)
      ;; TODO: error handling?
      200 (let [llm-response (get-in response [:body :response])
                json-ld-string (prepare-llm-response llm-response)
                model (jsonld/json-ld-string->model json-ld-string)
                skolemized-model (skolem/skolemize-model model "phi4")
                skolemized-json-ld-string (jsonld/model->json-ld-string skolemized-model)
                validation (validator/validate-model skolemized-model)]
            {:status 200
             :body {:json-ld-string skolemized-json-ld-string :invalid-nodes (:invalid-nodes validation)}
             :headers {"content-type" "application/ld+json"}})
      ;; TODO: error handling
      {:status 500 :body (pr-str response)})
   ))

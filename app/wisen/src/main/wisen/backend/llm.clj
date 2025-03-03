(ns wisen.backend.llm
  (:require [clj-http.client :as client]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.skolem :as skolem]
            [wisen.backend.rdf-validator :as validator]
            [clojure.string :as str]))

(defn format-ollama-prompt [prompt]
  ;; TODO: format (somehow requests fail with line breaks)
  (format "Convert the following natural language description into JSON-LD using only Schema.org vocabulary. Ensure that: 1. The output strictly follows Schema.org definitions. 2. All provided information is included. 3. No additional information is added. 4. The output is valid JSON-LD. 5. The response contains only the JSON-LD, with no explanations, headers, or formatting outside the JSON structure. Input: %s Output: " prompt))

(defn make-ollama-request-string [prompt]
  (str "{\"model\": \"phi4\", \"stream\": false, \"prompt\": \"" prompt "\"}"))

(defn extract-json-ld
  [s]
  (-> s
      (str/replace #"^```json\n|json-ld\n" "")
      (str/replace #"\n```$" "")))

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
                json-ld-string (extract-json-ld llm-response)
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

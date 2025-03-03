(ns wisen.backend.llm
  (:require [clj-http.client :as client]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.skolem :as skolem]
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
                _ (println (pr-str json-ld-string))
                model (jsonld/json-ld-string->model json-ld-string)
                _ (println (pr-str model))
                skolemized-model (skolem/skolemize-model model "phi4")
                _ (println (pr-str skolemized-model))
                skolemized-json-ld-string (jsonld/model->json-ld-string skolemized-model)
                _ (println skolemized-json-ld-string)]
            {:status 200
             :body skolemized-json-ld-string
             :headers {"content-type" "application/ld+json"}})
      ;; TODO: error handling
      {:status 500 :body (pr-str response)})
   ))

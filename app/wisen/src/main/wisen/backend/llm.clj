(ns wisen.backend.llm
  (:require [clj-http.client :as client]
            [clojure.string :as str]))

(defn format-ollama-prompt [prompt]
  ;; TODO: format (somehow requests fail with line breaks)
  (format "Convert the following natural language description into JSON-LD using only Schema.org vocabulary. Ensure that: 1. The output strictly follows Schema.org definitions. 2. All provided information is included. 3. No additional information is added. 4. The output is valid JSON-LD. 5. The response contains only the JSON-LD, with no explanations, headers, or formatting outside the JSON structure. Input: %s Output: " prompt))

(defn make-ollama-request-string [prompt]
  (println prompt)
  (str "{\"model\": \"phi4\", \"stream\": false, \"prompt\": \"" prompt "\"}"))

(defn ollama-request! [prompt]
  (client/post "http://localhost:11434/api/generate" {:content-type :json
                                                      :accept :json
                                                      :as :json
                                                      :body (make-ollama-request-string
                                                             (format-ollama-prompt prompt))}))
(defn extract-json-ld
  [s]
  (-> s
      (str/replace #"^```json\n" "")
      (str/replace #"\n```$" "")))

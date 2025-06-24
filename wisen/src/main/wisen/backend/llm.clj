(ns wisen.backend.llm
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [jsonista.core :as jsonista]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.rdf-validator :as validator]
            [wisen.common.or-error :as error]
            [cheshire.core :as cheshire])
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

(def ollama-model (or (System/getenv "OLLAMA_MODEL") "phi4"))

(defn make-ollama-request-string [prompt]
  (str "{\"model\": \"" ollama-model "\", \"stream\": false, \"prompt\": \"" prompt "\"}"))

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

(def ollama-request-url
  (str (or (System/getenv "OLLAMA_HOST")  "http://localhost:11434")
        "/api/chat"))

(defn try-parse-json-ld-string [json-ld-str]
  (try
    (let [model (jsonld/json-ld-string->model json-ld-str)
          validation (validator/validate-model model)
          res {:json-ld-string (jsonld/model->json-ld-string model)
               :invalid-nodes (:invalid-nodes validation)}]
      (error/make-success res))
    (catch Exception e
      (error/make-error (pr-str e)))))

(defn- make-chat-item [role content]
  {:role role
   :content content})

(defn- chat-item-role [x]
  (:role x))

(defn- chat-item-content [x]
  (:content x))

(defn- chat-history-item-f [acc it]
  (let [role (get it :role)
        content (get it :content)]
    (if (and (string? content)
             (or (= role "system")
                 (= role "user")
                 (= role "assistant")))
      (conj acc (make-chat-item role content))
      (reduced nil))))

(defn- deserialize-chat-history [params]
  (when-let [hist (get params :history)]
    (reduce chat-history-item-f [] hist)))

(defn- history-item->ollama [it]
  {:role (chat-item-role it)
   :content (chat-item-content it)})

(defn- chat-history->ollama [history]
  (map history-item->ollama history))

(defn- prepend-system-prompt [history]
  (concat
   [(make-chat-item
     "system"
     (format
      "You convert natural language descriptions into JSON-LD using only Schema.org vocabulary. Ensure that: %s %s"
      ensurances
      guidelines))]
   history))

(defn ollama-request! [params]
  (println (pr-str params))
  (if-let [chat-history (deserialize-chat-history params)]
    (let [_ (println "sending: " (pr-str (chat-history->ollama
                                          (prepend-system-prompt
                                           chat-history))))
          response
          (client/post ollama-request-url {:content-type :json
                                           :accept :json
                                           :as :json
                                           :body (cheshire/generate-string
                                                  {:model ollama-model
                                                   :stream false
                                                   :messages (chat-history->ollama
                                                              (prepend-system-prompt
                                                               chat-history))})})]
      (println " --- " (pr-str response))
      (case (:status response)
        200 (let [parsed-response (-> response
                                      (get-in [:body :message :content])
                                      (prepare-llm-response)
                                      (try-parse-json-ld-string))]
              (if (error/success? parsed-response)
                {:status 200
                 :body (error/success-value parsed-response)}
                {:status 500
                 :body {:error (error/error-value parsed-response)
                        :ai-response (:body response)}}))
        ;; TODO: error handling
        {:status 500 :body (pr-str (:body response))}))
    {:status 400
     :body "could not parse chat history"}))

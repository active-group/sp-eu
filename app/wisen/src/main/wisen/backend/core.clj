(ns wisen.backend.core
  (:gen-class)
  (:require [ring.adapter.jetty :as ring-jetty]
            [ring.middleware.resource])
  (:import
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)))

(def model
  (let [model (ModelFactory/createDefaultModel)
        hirsch (.createResource model "http://wisen.active-group.de/resource/a12345")
        _ (.addProperty hirsch SchemaDO/name "Hirsch Begegnungsstätte für Ältere e.V.")
        _ (.addProperty hirsch SchemaDO/email "hirsch-begegnung@t-online.de")
        stadtseniorenrat (.createResource model "http://wisen.active-group.de/resource/b9876")
        _ (.addProperty stadtseniorenrat SchemaDO/name "Stadtseniorenrat Tübingen e.V.")
        _ (.addProperty stadtseniorenrat SchemaDO/email "info@stadtseniorenrat-tuebingen.de")
        _ (.addProperty stadtseniorenrat SchemaDO/url "https://www.stadtseniorenrat-tuebingen.de")]
    model))

(defn handler [_request]
  (let [result-str (with-out-str
                     (.write model *out* "JSON-LD"))]
    {:status 200
     :headers {"content-type" "application/ld+json"}
     :body result-str}))

(def main-handler
  (ring.middleware.resource/wrap-resource handler "/"))

(defonce server (atom nil))

(defn start-server! []
  (reset! server
          (ring-jetty/run-jetty
           main-handler
           {:port 4321
            :join? false})))

(defn stop-server! []
  (when-some [jetty @server]
    (reset! server nil)
    (.stop jetty)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Wisen Store")
  (start-server!))

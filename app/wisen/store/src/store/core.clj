(ns store.core
  (:gen-class)
  (:require [ring.adapter.jetty :as ring-jetty]
            [ring.middleware.resource])
  (:import
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)))

(def model
  (let [model (ModelFactory/createDefaultModel)
        hirsch (.createResource model "http://wisen.active-group.de/resource/a12345")
        _ (.addProperty hirsch SchemaDO/name "Hirsch Begegnungsstätte für Ältere e.V.")]
    model))

(defn handler [_request]
  (let [result-str (with-out-str
                     (.write model *out* "JSON-LD"))]
    {:status 200
     :headers {"content-type" "ld+json"}
     :body result-str}))

(def main-handler
  (ring.middleware.resource/wrap-resource handler "public"))

(def server (atom nil))

(defn- start-server! []
  (reset! server
          (ring-jetty/run-jetty
           main-handler
           {:port 4321
            :join? false})))

(defn- stop-server! []
  (reset! server (.stop @server)))

(start-server!)
#_(stop-server!)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Wisen Store")
  (start-server!))

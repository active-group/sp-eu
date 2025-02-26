(ns wisen.backend.handler
  (:require [hiccup.core :as h]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.middleware.muuntaja :as m]
            [ring.middleware.resource]
            [reacl-c-basics.pages.ring :as pages.ring]
            [muuntaja.core]
            [wisen.frontend.routes :as frontend.routes]
            [wisen.backend.triple-store :as triple-store]
            [wisen.backend.resource :as r]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.llm :as llm]
            [wisen.backend.osm :as osm]
            [clojure.edn :as edn]
            [wisen.common.change-api :as change-api])
  (:import
   (org.apache.jena.tdb2 TDB2 TDB2Factory)
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.vocabulary SchemaDO)
   (org.apache.jena.query ReadWrite QueryExecutionFactory)))

(defn mint-resource-url! []
  (str "http://example.org/resource/" (random-uuid)))

(defn create-resource [_request]
  {:status 200
   :body {:id (mint-resource-url!)}})

(defn get-resource-description [request]
  (let [id (get-in request [:path-params :id])
        uri (r/uri-for-resource-id id)
        ;; TODO: injection!
        q (str
           "CONSTRUCT {<"
           uri
           "> ?p ?o .}
          WHERE { <"
           uri
           "> ?p ?o . }")
        result-model
        (triple-store/run-construct-query! q)]
    {:status 200
     :body (with-out-str
             (.write result-model *out* "JSON-LD"))}))

(defn get-resource [request]
  (try
    (let [id (java.util.UUID/fromString
              (get-in request [:path-params :id]))]
      {:status 303
       :headers {"Location" (r/description-url-for-resource-id id)}})
    (catch Exception _e
      {:status 400})))

(defn search [request]
  (let [q (get-in request [:body-params :query])
        result-model (triple-store/run-construct-query! q)]
    {:status 200
     :body (jsonld/model->json-ld-string result-model)}))

(defn add-triples [request]
  (let [body (slurp (get-in request [:body]))
        model (jsonld/json-ld-string->model body)]
    ;; merge model into triple store
    (triple-store/add-model! model)
    {:status 200}))

(defn edit-triples [request]
  (let [changes (map change-api/edn->change
                     (get-in request
                             [:body-params :changes]))]
    (triple-store/edit-model! changes)
    {:status 200}))

(defn osm-lookup [request]
  (let [osmid (get-in request [:path-params :osmid])]
    (osm/lookup! osmid)))

(defn ollama-handler [request]
  (let [body (slurp (get request :body))
        x (llm/ollama-request! body)]
    (println "==================")
    (println (pr-str x))
    x))

(def handler*
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/search" {:post {:handler search}}]
      ["/resource" {:post {:handler create-resource}}]
      ["/resource/:id" {:get {:handler get-resource-description}}]
      ["/triples" {:post {:handler add-triples}
                   :put {:handler edit-triples}}]]

     ["/osm"
      ["/lookup/:osmid" {:get {:handler osm-lookup}}]]

     ;; URIs a la http://.../resource/abcdefg are identifiers. They
     ;; don't directly resolve to a description. We use 303
     ;; redirection to move clients over to /api/resource/abcdefg
     ["/resource/:id" {:get {:handler get-resource}}]
     ["/describe" {:post {:handler ollama-handler}}]]

    ;; router data affecting all routes
    {:data {:muuntaja muuntaja.core/instance
            :coercion rcs/coercion
            :middleware [m/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

(def client-response
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (h/html [:html
                  [:head
                   [:title "Wisen Web"]
                   [:meta {:charset "utf-8"}]
                   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                   [:style "html, body {margin: 0; padding: 0;
                                        font-family: 'Helvetica Neue', Helvetica, sans-serif;}
                            ul, ol {margin: 0; padding: 0; padding-left: 1.4em;}"]
                   ;; TODO: use these two leaflet resources via node module (package.json)
                   [:link {:rel "stylesheet"
                           :href "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
                           :integrity "sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
                           :crossorigin ""}]
                   [:script {:src "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
                             :integrity "sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo="
                             :crossorigin ""}]]
                  [:body
                   [:div {:id "main"}]
                   [:script {:type "text/javascript"
                             :src "/js/main.js"
                             :charset "UTF-8"}]]])})

(defn- wrap-caching [handler default]
  (fn [request]
    (let [response (handler request)]
      (when response
        (update-in response [:headers "cache-control"]
                   (fn [v]
                     (or v default)))))))

(def handler
  (-> handler*
      (ring.middleware.resource/wrap-resource "/")
      (wrap-caching "max-age=0")
      (pages.ring/wrap-client-routes frontend.routes/routes client-response)))

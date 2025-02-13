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
            [wisen.backend.jsonld :as jsonld])
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
       :headers {"Location" (r/description-url-for-resource-id id)}}
      )
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

(def handler*
  (ring/ring-handler
   (ring/router
    [["/" {:get {:handler (fn [_] {:status 200 :body (slurp (clojure.java.io/resource "index.html"))})}}]
     ["/api"
      ["/search" {:post {:handler search}}]
      ["/resource" {:post {:handler create-resource}}]
      ["/resource/:id" {:get {:handler get-resource-description}}]
      ["/triples" {:post {:handler add-triples}}]]

     ;; URIs a la http://.../resource/abcdefg are identifiers. They
     ;; don't directly resolve to a description. We use 303
     ;; redirection to move clients over to /api/resource/abcdefg
     ["/resource/:id" {:get {:handler get-resource}}]]

    ;; router data affecting all routes
    {:data {:muuntaja muuntaja.core/instance
            :coercion rcs/coercion
            :middleware [m/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

#_(handler* {:request-method :post
           :uri "/api/resource"})

#_(handler* {:request-method :get
             :uri "/api/resource/foo"})

(def client-response
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (h/html [:html
                  [:head
                   [:title "Wisen Web"]
                   [:meta {:charset "utf-8"}]
                   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                   [:style "html, body {margin: 0; padding: 0;}"]]
                  [:body
                   [:div {:id "main"}]
                   [:script {:type "text/javascript"
                             :src "/js/main.js"
                             :charset "UTF-8"}]]])})

(def handler
  (-> handler*
      (ring.middleware.resource/wrap-resource "/")
      (pages.ring/wrap-client-routes frontend.routes/routes client-response)))

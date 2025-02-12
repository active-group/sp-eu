(ns wisen.backend.handler
  (:require [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.middleware.muuntaja :as m]
            [ring.middleware.resource]
            [muuntaja.core])
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

(defn get-resource [request]
  (let [id (get-in request [:path-params :id])]
    {:status 200
     :body {:id id}}))

(def handler*
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/resource" {:post {:handler create-resource}}]
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

(def handler
  (ring.middleware.resource/wrap-resource handler* "/"))

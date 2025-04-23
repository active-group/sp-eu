(ns wisen.backend.handler
  (:require [active.clojure.config :as active.config]
            [hiccup.core :as h]
            [reitit.ring :as ring]
            [clj-http.client :as http]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.exception :as exception]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.middleware.muuntaja :as m]
            [ring.middleware.resource]
            [ring.middleware.session :as mw.session]
            [ring.middleware.session.memory :as session.memory]
            [reacl-c-basics.pages.ring :as pages.ring]
            [muuntaja.core]
            [wisen.backend.auth :as auth]
            [wisen.backend.config :as config]
            [wisen.backend.triple-store :as triple-store]
            [wisen.backend.resource :as r]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.llm :as llm]
            [wisen.backend.osm :as osm]
            [wisen.backend.overpass :as overpass]
            [wisen.backend.sparql :as sparql]
            [wisen.common.routes :as routes]
            [clojure.edn :as edn]
            [wisen.common.change-api :as change-api]
            [wisen.common.prefix :as prefix])
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
    (let [id (get-in request [:path-params :id])]
      {:status 303
       :headers {"Location" (r/description-url-for-resource-id id)}})
    (catch Exception _e
      {:status 400})))

(defn search [request]
  (let [q (get-in request [:body-params :query])
        result-model (triple-store/run-construct-query! q)]
    {:status 200
     :body (jsonld/model->json-ld-string result-model)}))

(defn add-changes [request]
  (let [changeset (change-api/edn->changeset
                   (get-in request
                           [:body-params :changes]))]
    (triple-store/edit-model! changeset)
    ;; TODO: a bit wasteful to derive geo location on _every_ update
    ;; strictly neccessary only when address is added
    (triple-store/decorate-geo!)
    {:status 200}))

(defn osm-lookup [request]
  (let [osmid (get-in request [:path-params :osmid])]
    (osm/lookup! osmid)))

(defn osm-search [request]
  (let [query (slurp (:body request))
        _ (println "got query: " (pr-str query))

        address-sparql (sparql/parse-query-string query)
        _ (println "parsed address: " (pr-str address-sparql))

        address-osm (osm/address
                     osm/address-country
                     (sparql/address-country-literal-value address-sparql)

                     osm/address-locality
                     (sparql/address-locality-literal-value address-sparql)

                     osm/address-postcode
                     (sparql/postal-code-literal-value address-sparql)

                     osm/address-street
                     (sparql/street-address-literal-value address-sparql))

        address-search-result (osm/search! address-osm)
        _ (println "OSM result: " (pr-str address-search-result))]

    (cond
      (osm/search-success? address-search-result)
      {:status 200
       :headers {"Content-type" "application/json"}
       :body
       (do
         (println (pr-str (sparql/pack-search-result
                           address-sparql
                           (osm/search-success-longitude address-search-result)
                           (osm/search-success-latitude address-search-result))))
         (sparql/pack-search-result
          address-sparql
          (osm/search-success-longitude address-search-result)
          (osm/search-success-latitude address-search-result)))}

      (osm/search-failure? address-search-result)
      {:status 500 :body (pr-str
                          (osm/search-failure-error
                           address-search-result))})))

(defn osm-search-area [request]
  (let [bbox (:body-params request)]
    (overpass/search-area! bbox "amenity" "restaurant")))

(defn ollama-handler [request]
  (let [body (slurp (get request :body))]
    (llm/ollama-request! body)))

(def schema (slurp "public/schema/schemaorg.jsonld"))

(defn get-schema [request]
  {:status 200
   :body schema
   :headers {"Content-type" "application/ld+json"}})

(def handler*
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/search" {:post {:handler search}}]
      ["/resource/:id" {:get {:handler get-resource-description}}]
      ["/changes" {:post {:handler add-changes}}]
      ["/schema" {:get {:handler get-schema}}]]

     ["/osm"
      ["/lookup/:osmid" {:get {:handler osm-lookup}}]
      ["/search" {:post {:handler osm-search}}]
      ["/search-area" {:post {:handler osm-search-area}}]]

     ;; URIs a la http://.../resource/abcdefg are identifiers. They
     ;; don't directly resolve to a description. We use 303
     ;; redirection to move clients over to /api/resource/abcdefg
     ["/resource/:id" {:get {:handler get-resource}}]
     ["/describe" {:post {:handler ollama-handler}}]]

    ;; router data affecting all routes
    {:data {:muuntaja muuntaja.core/instance
            :coercion rcs/coercion
            :middleware [m/format-middleware
                         exception/exception-middleware
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
                   [:link {:rel "icon" :href "favicon.png" :type "image/png"}]
                   [:style "html, body {margin: 0; padding: 0;
                                        font-family: 'Helvetica Neue', Helvetica, sans-serif;}
                            ul, ol {margin: 0; padding: 0; padding-left: 1.4em;}
                            hr {margin: 0; padding: 0; border: 0; border-bottom: 1px solid gray;}

    @keyframes rotation {
    0% {
        transform: rotate(0deg);
    }
    100% {
        transform: rotate(360deg);
    }
    }
"]
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
                   [:script {:type "text/javascript"} prefix/set-prefix-code]
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

(defonce ^:private session-store (session.memory/memory-store))

(defn handler [cfg]
  (let [wrap-session  (fn session-mw [handler]
                        (mw.session/wrap-session handler {:store session-store}))
        openid-config  (active.config/section-subconfig cfg
                                                        config/auth-section)
        free-for-all? (active.config/access cfg
                                            config/free-for-all?-setting
                                            config/auth-section)
        wrap-sso      (auth/mk-sso-mw free-for-all? openid-config)]

    (-> handler*
        (ring.middleware.resource/wrap-resource "/")
        (wrap-caching "max-age=0")
        (pages.ring/wrap-client-routes routes/routes client-response)
        (wrap-sso)
        (wrap-session))))

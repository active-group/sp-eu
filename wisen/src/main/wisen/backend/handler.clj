(ns wisen.backend.handler
  (:require [active.clojure.config :as active.config]
            [hiccup.core :as h]
            [reitit.ring :as ring]
            [clojure.java.io]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.exception :as exception]
            [reitit.coercion.spec :as rcs]
            [reitit.ring.middleware.muuntaja :as m]
            [ring.middleware.resource]
            [ring.middleware.session :as mw.session]
            [ring.middleware.session.memory :as session.memory]
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
            [reacl-c-basics.pages.routes :as pages.routes]
            [wisen.common.change-api :as change-api]
            [wisen.common.prefix :as prefix]))

(defn mint-resource-url! []
  (str "http://example.org/resource/" (random-uuid)))

(defn create-resource [_request]
  {:status 200
   :body {:id (mint-resource-url!)}})

(declare client-response)

(defn get-resource-description [request]
  (let [accept (get-in request [:headers "accept"])]
    (if (or (re-find #"application/ld\+json" accept)
            (re-find #"application/json" accept))
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
         ;; We have to take the "Accept" header of the request
         ;; into account for the cache key in the
         ;; browser. Otherwise, a client might show a JSON-LD
         ;; response when the user clicks the back-button for
         ;; example.
         :headers {"Vary" "Accept"}
         :body (with-out-str
                 (.write result-model *out* "JSON-LD"))})

      ;; else show app
      client-response)))

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

(defn semantic-area-search [request]
  (let [params (:body-params request)]
    (overpass/semantic-area-search! (:semantic-area-search-query params)
                                    (:semantic-area-search-bbox params))))

(defn ollama-handler [request]
  (let [params (:body-params request)]
    (llm/ollama-request! params)))

(def schema
  (slurp (clojure.java.io/resource "schema/schemaorg.jsonld")))

(defn get-schema [request]
  {:status 200
   :body schema
   :headers {"Content-type" "application/ld+json"}})

(defn get-references [request]
  (let [id (get-in request [:path-params :id])
        uri (r/uri-for-resource-id id)
        q (str "SELECT DISTINCT ?reference WHERE { ?reference ?p <" uri "> . }")
        result (triple-store/run-select-query! q)]
    {:status 200
     :headers {"Content-type" "application/json"}
     :body (map (fn [line]
                  (.toString (get line "reference")))
                result)}))

(def handler*
  (ring/ring-handler
   (ring/router
    [["/api"
      ["/search" {:post {:handler search}}]
      ["/changes" {:post {:handler add-changes}}]
      ["/schema" {:get {:handler get-schema}}]
      ["/references/:id" {:get {:handler get-references}}]]

     ["/osm"
      ["/lookup/:osmid" {:get {:handler osm-lookup}}]
      ["/search" {:post {:handler osm-search}}]
      ["/search-area" {:post {:handler osm-search-area}}]
      ["/semantic-area-search" {:post {:handler semantic-area-search}}]]

     ;; URIs a la http://.../resource/abcdefg are identifiers. They
     ;; don't directly resolve to a description. We use 303
     ;; redirection to move clients over to /resource/abcdefg/about
     ["/resource/:id" {:get {:handler get-resource}}]
     ["/resource/:id/about" {:get {:handler get-resource-description}}]
     ["/ask" {:post {:handler ollama-handler}}]]

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
                            a, button { position: relative; }
                            a:active, button:active { top: 1px; opacity: 0.7; }

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

(defn- wrap-client-fn-routes
  [handler routes client-fn]
  (fn [request]
    (or
     ;; try handler first
     (when-let [response (handler request)]
       (when-not (= (:status response) 404)
         response))
     ;; fallback: try routes
     (if-let [route (first (filter #(pages.routes/route-matches % request) routes))]
       ;; TODO: really call client-fn with route?
       (apply client-fn route (pages.routes/route-matches route request))
       {:status 404}))))

(defn- wrap-client-routes
  [handler routes client]
  (wrap-client-fn-routes handler routes (constantly client)))

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
        (wrap-client-routes routes/routes client-response)
        (wrap-sso)
        (wrap-session))))

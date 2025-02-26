(ns wisen.backend.osm
  (:require [clj-http.client :as http]
            [cheshire.core :as cheshire]))

(defn osm-id->osm-uri [osm-id]
  (let [osm-id* (subs osm-id 1)
        type (case (first osm-id)
               \N "node"
               \W "way"
               \R "relation")]
    (str "https://www.openstreetmap.org/" type "/" osm-id*)))

(defn osm-id->place-uri [osm-id]
  (str (osm-id->osm-uri osm-id) "/place"))

(defn osm-id->address-uri [osm-id]
  (str (osm-id->place-uri osm-id) "/address"))

(defn osm-id->geo-uri [osm-id]
  (str (osm-id->place-uri osm-id) "/geo"))

(defn- parse-osm-response [osm-id body]
  (let [entry (first body)
        lon (bigdec (get entry "lon"))
        lat (bigdec (get entry "lat"))
        address (get entry "address")
        country-code (get address "country_code")
        town (get address "town")
        postcode (get address "postcode")
        road (get address "road")
        number (get address "house_number")]

    {"@id" (osm-id->osm-uri osm-id)
     "@type" "http://schema.org/Place"
     "http://schema.org/geo" {"@id" (osm-id->geo-uri osm-id)
                              "@type" "http://schema.org/GeoCoordinates"
                              "http://schema.org/longitude" lon
                              "http://schema.org/latitude" lat}
     "http://schema.org/address" {"@id" (osm-id->address-uri osm-id)
                                  "@type" "http://schema.org/PostalAddress"
                                  "http://schema.org/addressCountry" country-code
                                  "http://schema.org/addressLocality" town
                                  "http://schema.org/postalCode" postcode
                                  "http://schema.org/streetAddress" (str road " " number)}
     }))

(defn lookup! [osm-id]
  (try
    (let [res (http/get
               (str
                "https://nominatim.openstreetmap.org/lookup?format=jsonv2&osm_ids="
                osm-id)
               {:accept :json
                :as :json-string-keys})
          status (:status res)
          body (:body res)]
      (cond
        (= 200 status)
        (if (and (coll? body) (not-empty body))
          {:status 200
           :headers {"Content-type" "application/ld+json"}
           :body (cheshire/generate-string (parse-osm-response osm-id body))}
          ;; else
          {:status 404
           :body "OSM ID not found"})

        (= status 404)
        {:status 404
         :body "Resource not found"}

        (>= status 500)
        {:status status
         :body "Server error, please try again later"}

        :else
        {:status status
         :body "Unexpected response from server"}
        ))

    (catch Exception e
      {:status 500 :body (str "Request failed: " (.getMessage e))})))

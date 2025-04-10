(ns wisen.backend.overpass
  (:require [active.data.record :refer [def-record]]
            [active.data.realm :as realm]
            [clj-http.client :as http]
            [cheshire.core :as cheshire]
            [ring.util.codec :as codec]))

(defn- parse-overpass-response-item [item]
  (let [lon (bigdec (get item "lon"))
        lat (bigdec (get item "lat"))
        #_#_#_#_#_#_#_#_#_#_#_#_address (get entry "address")
        country-code (get address "country_code")
        town (get address "town")
        postcode (get address "postcode")
        road (get address "road")
        number (get address "house_number")]

    {"@id" (str "http://overpass-items24.de/vu/" (get item "id"))
     "@type" "http://schema.org/Place"
     "http://schema.org/geo" {"@id" (str "http://overpass-items24.de/vu/geo" (str lon lat))
                              "@type" "http://schema.org/GeoCoordinates"
                              "http://schema.org/longitude" lon
                              "http://schema.org/latitude" lat}
     #_#_"http://schema.org/address" {"@id" (osm-id->address-uri osm-id)
                                  "@type" "http://schema.org/PostalAddress"
                                  "http://schema.org/addressCountry" country-code
                                  "http://schema.org/addressLocality" town
                                  "http://schema.org/postalCode" postcode
                                  "http://schema.org/streetAddress" (str road " " number)}
     }))

(defn make-search-query-component
  [type tag-key tag-value [[min-lat max-lat] [min-long max-long]]]
  (str type "[" tag-key "=" tag-value "](" min-lat "," min-long "," max-lat "," max-long ");"))

(def body-atom (atom nil))

(def tübb [[48.484 48.550]
           [9.0051 9.106]])

(defn search-area! [bounding-box tag-key tag-value]
  (let [overpass-query (str "[out:json];"
                            "("
                            (make-search-query-component "node" tag-key tag-value bounding-box)
                            ;; (make-search-query-component "way" tag-key tag-value bounding-box)
                            ;; (make-search-query-component "relation" tag-key tag-value bounding-box)
                            ");"
                            "out body;"
                            ">;"
                            "out skel qt;")
        encoded-query (codec/url-encode overpass-query)
        url (str "https://overpass-api.de/api/interpreter?data=" encoded-query)
        res (http/get url
                      {:accept :json
                       :as :json-string-keys})
        status (:status res)
        body (:body res)
        _ (println (str "body: \n\n" (pr-str (get body "elements")) "\n\n"))
        response (map parse-overpass-response-item
                   (get body "elements"))
        _ (println (str "response: \n\n" (pr-str response) "\n\n"))]
    (cond
      (= 200 status)
      {:status 200
       :headers {"Content-type" "application/ld+json"}
       :body (cheshire/generate-string response)}

      :else
      (do
        (println (str "error trying to search an area:\n\tstatus: " (pr-str status) "\n\tbody: " (pr-str body)))
        {:status status}))))


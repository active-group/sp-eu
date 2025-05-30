(ns wisen.backend.overpass
  (:require [clj-http.client :as http]
            [cheshire.core :as cheshire]
            [ring.util.codec :as codec]
            [wisen.common.prefix :as prefix]
            [wisen.backend.osm-semantic-search :as sem]))

(defn assoc-some-from [m1 m2 m1-key m2-key]
  (when-let [v (get m2 m2-key)]
    (assoc m1 m1-key v)))

(defn assoc-some [m k v]
  (if v
    (assoc m k v)
    m))

(defn assoc-some-street-address [m tags]
  (let [street (get tags "addr:street")
        housenumber (get tags "addr:housenumber")
        street-address (if (and street housenumber)
                         (str street " " housenumber)
                         street)]
    (assoc-some m "http://schema.org/streetAddress" street-address)))

(defn assoc-some-address-map [m address-map*]
  (let [address-map (when address-map*
                                 (merge {"@type" "http://schema.org/PostalAddress"}
                                        address-map*))]
    (assoc-some m "http://schema.org/address" address-map)))

(defn parse-address [tags]
  (-> {}
      (assoc-some-from tags "http://schema.org/addressCountry" "addr:country")
      (assoc-some-from tags "http://schema.org/addressLocality" "addr:city")
      (assoc-some-from tags "http://schema.org/postalCode" "addr:postcode")
      (assoc-some-street-address tags)))

(defn parse-overpass-response-item [item]
  (let [lon* (get item "lon")
        lat* (get item "lat")
        id (get item "id")
        tags (get item "tags")
        address-map (parse-address tags)]
    (when (and lon* lat* id)
      (let [lon (bigdec lon*)
            lat (bigdec lat*)]
        (-> {}
            (assoc "@type"  "http://schema.org/Organization")
            (assoc-some "http://schema.org/name" (get tags "name"))
            (assoc-some "http://schema.org/url" (get tags "website"))
            (assoc "http://schema.org/location"
                   (-> {}
                       (assoc "http://schema.org/geo" {"@type" "http://schema.org/GeoCoordinates"
                                                       "http://schema.org/longitude" lon
                                                       "http://schema.org/latitude" lat})
                       (assoc-some-address-map address-map))))))))

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
        response (remove nil?
                         (map parse-overpass-response-item
                              (take 2 (get body "elements"))))]
    (cond
      (= 200 status)
      {:status 200
       :headers {"Content-type" "application/ld+json"}
       :body (cheshire/generate-string response)}

      :else
      {:status status})))

(defn semantic-area-search! [query [[min-lat max-lat] [min-long max-long]]]
  (let [response (sem/semantic-osm-search query min-lat min-long max-lat max-long)
        results (map parse-overpass-response-item (:results response))]
    {:status 200
     :headers {"Content-type" "application/ld+json"}
     :body (cheshire/generate-string results)}))

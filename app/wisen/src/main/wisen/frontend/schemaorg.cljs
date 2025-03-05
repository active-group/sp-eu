(ns wisen.frontend.schemaorg
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(def ^:private predicate-priority
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/keywords"
   "http://schema.org/location"
   "http://schema.org/sameAs"

   "http://schema.org/streetAddress"
   "http://schema.org/postalCode"
   "http://schema.org/addressLocality"
   "http://schema.org/addressCountry"

   "http://schema.org/openingHoursSpecification"

   "http://schema.org/email"

   "http://schema.org/dayOfWeek"
   "http://schema.org/opens"
   "http://schema.org/closes"

   ])

(defn- index-of [s v]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (= v (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn compare-predicate [p1 p2]
  (let [i1 (index-of predicate-priority p1)
        i2 (index-of predicate-priority p2)]
    (if i1
      (if i2
        (compare i1 i2)
        -1)
      (if i2
        1
        (compare p1 p2)))))

;;

(def types
  {"http://schema.org/Thing"
   "Thing"

   "http://schema.org/Organization"
   "Organization"

    "http://schema.org/GeoCoordinates"
    "Geo coordinates"

    "http://schema.org/GeoCircle"
    "Geo circle"

    "http://schema.org/PostalAddress"
    "Address"

    "http://schema.org/OpeningHoursSpecification"
    "Opening hours"
   })

(defn- map-keys [f m]
  (reduce (fn [acc [k v]]
            (assoc acc (f k) v))
          {}
          m))

(def tree-sorts
  (merge
   {tree/literal-string "String"
    tree/literal-decimal "Decimal"}
   (map-keys tree/make-node types)))

(defn tree-sorts-for-predicate [p]
  (case p
    "https://wisen.active-group.de/osm-uri"
    [tree/literal-string]

    "http://schema.org/name"
    [tree/literal-string]

    "http://schema.org/email"
    [tree/literal-string]

    "http://schema.org/url"
    [tree/literal-string]

    "http://schema.org/geo"
    [tree/literal-string
     (tree/make-node "http://schema.org/GeoCoordinates")]

    ;; default
    (keys tree-sorts)))

(def predicates
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/url"
   #_"http://schema.org/areaServed"
   "http://schema.org/location"
   "http://schema.org/sameAs"
   #_"http://schema.org/geo"
   "http://schema.org/email"
   "http://schema.org/openingHoursSpecification"
   ])

(def default-predicate
  "http://schema.org/name")

(defn predicates-for-type [type]
  (case (tree/node-uri type)
    "http://schema.org/Thing"
    predicates

    "http://schema.org/Organization"
    ["http://schema.org/name"
     "http://schema.org/description"
     "http://schema.org/url"
     "http://schema.org/location"]

    "http://schema.org/GeoCoordinates"
    ["http://schema.org/latitude"
     "http://schema.org/longitude"]

    "http://schema.org/GeoCircle"
    ["http://schema.org/geoMidPoint"
     "http://schema.org/geoRadius"]

    "http://schema.org/PostalAddress"
    ["http://schema.org/addressCountry"
     "http://schema.org/addressLocality"
     "http://schema.org/addressRegion"
     "http://schema.org/postalCode"
     "http://schema.org/streetAddress"]

    ;; default
    predicates))

(defn pr-predicate [p]
  (case p
    "https://wisen.active-group.de/osm-uri"
    "OpenStreetMap URI"

    "http://schema.org/name"
    "Name"

    "http://schema.org/email"
    "E-Mail"

    "http://schema.org/url"
    "Website"

    "http://schema.org/geo"
    "The geo coordinates of the place"

    "http://schema.org/description"
    "Description"

    "http://schema.org/keywords"
    "Keywords"

    "http://schema.org/areaServed"
    "Area served"

    "http://schema.org/latitude"
    "Latitude"

    "http://schema.org/longitude"
    "Longitude"

    "http://schema.org/location"
    "Location"

    "http://schema.org/addressCountry"
    "Country (e.g. 'de' for Germany)"

    "http://schema.org/addressLocality"
    "Locality or town (e.g. 'Berlin')"

    "http://schema.org/postalCode"
    "Postal code"

    "http://schema.org/streetAddress"
    "Street Address"

    "http://schema.org/sameAs"
    "Same resource on other site (Google Maps, OpenStreetMap, ...)"

    "http://schema.org/openingHoursSpecification"
    "Opening hours"

    "http://schema.org/dayOfWeek"
    "Day of week"

    "http://schema.org/opens"
    "Opens"

    "http://schema.org/closes"
    "Closes"

    p))

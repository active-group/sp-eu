(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(defn- make-type-property [s]
  (tree/make-property type-uri (tree/make-node s)))

(defn- schema [s]
  (str "http://schema.org/" s))

(def ^:private lit-s tree/make-literal-string)

;; TODO: how should we deal with the side-effecting (random-uuid)?

(defn- fresh-uri! []
  (str "http://wisen.active-group.de/resource/" (random-uuid)))

(def default-geo-coordinates
  (tree/make-node
   (fresh-uri!)
   [(make-type-property "http://schema.org/GeoCoordinates")
    (tree/make-property (schema "latitude") (lit-s "48.52105844145676"))
    (tree/make-property (schema "longitude") (lit-s "9.054090697517525"))]))

(defn default-object-for-predicate [pred]
  (cond
    (= pred "http://schema.org/name")
    (lit-s "Der gute Name")

    (= pred "http://schema.org/geo")
    default-geo-coordinates

    (= pred "http://schema.org/areaServed")
    (tree/make-node
     (fresh-uri!)
     [(make-type-property (schema "GeoCircle"))
      (tree/make-property (schema "geoMidpoint") default-geo-coordinates)
      (tree/make-property (schema "geoRadius") (lit-s "100"))])

    (= pred "http://schema.org/location")
    (tree/make-node
     (fresh-uri!)
     [(make-type-property (schema "Place"))
      (tree/make-property (schema "address") (tree/make-node
                                              (fresh-uri!)
                                              [(make-type-property "http://schema.org/PostalAddress")
                                               (tree/make-property (schema "addressCountry") (lit-s "DE"))
                                               (tree/make-property (schema "postalCode") (lit-s "72072"))
                                               (tree/make-property (schema "addressLocality") (lit-s "Tübingen"))
                                               (tree/make-property (schema "streetAddress") (lit-s "Hechinger Str. 12/1"))]))])

    :else
    (tree/make-literal-string "")
    ))

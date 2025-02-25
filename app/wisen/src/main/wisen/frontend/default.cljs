(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

;; TODO: how should we deal with the side-effecting (random-uuid)?

(defn- fresh-uri! []
  (str "http://wisen.active-group.de/resource/" (random-uuid)))

(def default-geo-coordinates
  (tree/make-node
   (fresh-uri!)
   [(tree/make-property type-uri (tree/make-node "http://schema.org/GeoCoordinates"))
    (tree/make-property "http://schema.org/latitude" (tree/make-literal-string "48.52105844145676"))
    (tree/make-property "http://schema.org/longitude" (tree/make-literal-string "9.054090697517525"))]))

(defn default-object-for-predicate [pred]
  (cond
    (= pred "http://schema.org/name")
    (tree/literal-string tree/literal-string-value "Der gute Name")

    (= pred "http://schema.org/geo")
    default-geo-coordinates

    (= pred "http://schema.org/areaServed")
    (tree/make-node
     (fresh-uri!)
     [(tree/make-property type-uri (tree/make-node "http://schema.org/GeoCircle"))
      (tree/make-property "http://schema.org/geoMidpoint" default-geo-coordinates)
      (tree/make-property "http://schema.org/geoRadius" (tree/make-literal-string "100"))])

    :else
    (tree/make-literal-string "")
    ))

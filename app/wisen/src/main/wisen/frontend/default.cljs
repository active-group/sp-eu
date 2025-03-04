(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]))

(def ^:private lit-s tree/make-literal-string)
(def ^:private lit-d tree/make-literal-decimal)

;; TODO: how should we deal with the side-effecting (random-uuid)?

(defn- fresh-uri! []
  (str "http://wisen.active-group.de/resource/" (random-uuid)))

(defn- schema [s]
  (str "http://schema.org/" s))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(defn- type-property [s]
  (tree/make-property type-uri (tree/make-node (schema s))))

(defn- make-node [type & props]
  (tree/make-node
   (fresh-uri!)
   (conj props (type-property type))))

(defn- property [pred obj]
  (tree/make-property (schema pred) obj))

(def default-geo-coordinates
  (make-node
   "GeoCoordinates"
   [(property "latitude" (lit-s "48.52105844145676"))
    (property "longitude" (lit-s "9.054090697517525"))]))

(def default-organization
  (make-node
   "Organization"
   (property "name" (lit-s "Name"))
   (property "description" (lit-s "Description"))))

(def default-postal-address
  (make-node
   "PostalAddress"
   (property "streetAddress" (lit-s "Hechinger Str. 12/1"))
   (property "postalCode" (lit-s "72072"))
   (property "addressLocality" (lit-s "Tübingen"))
   (property "addressCountry" (lit-s "DE"))))

(def default-place
  (make-node
   "Place"
   (property "address" default-postal-address)))

(def default-opening-hours-specification
  (make-node
   "OpeningHoursSpecification"
   (property "dayOfWeek" (tree/make-node (schema "Monday")))
   (property "opens" (lit-s "10:00:00"))
   (property "closes" (lit-s "17:00:00"))))

(defn default-object-for-type [type]
  (case type
    tree/literal-string
    (lit-s "...")

    "http://schema.org/GeoCoordinates"
    default-geo-coordinates

    "http://schema.org/Organization"
    default-organization

    "http://schema.org/Place"
    default-place

    "http://schema.org/PostalAddress"
    default-postal-address

    "http://schema.org/OpeningHoursSpecification"
    default-opening-hours-specification

    (lit-s "")))

(defn default-type-for-predicate [pred]
  (case pred
    "http://schema.org/name"
    tree/literal-string

    "http://schema.org/email"
    tree/literal-string

    "http://schema.org/description"
    tree/literal-string

    "http://schema.org/location"
    (schema "Place")

    "http://schema.org/openingHoursSpecification"
    (schema "OpeningHoursSpecification")

    "http://schema.org/address"
    (schema "PostalAddress")

    "http://schema.org/geo"
    (schema "GeoCoordinates")

    tree/literal-string))

(defn default-object-for-predicate [pred]
  (default-object-for-type
   (default-type-for-predicate pred)))

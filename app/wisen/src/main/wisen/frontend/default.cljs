(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]))

(def ^:private lit-s tree/make-literal-string)
(def ^:private lit-d tree/make-literal-decimal)
(def ^:private lit-b tree/make-literal-boolean)

(defn- schema [s]
  (str "http://schema.org/" s))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(defn- type-property [s]
  (tree/make-property type-uri (tree/make-node (schema s))))

(defn- make-node [type & props]
  (-> (tree/make-node)
      (tree/node-type type)
      (tree/node-properties props)))

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

(defn default-tree-for-sort [type]
  (cond
    (= type tree/literal-string)
    (lit-s "...")

    (= type tree/literal-decimal)
    (lit-d "1.0")

    (= type tree/literal-boolean)
    (lit-b "true")

    (= type tree/ref)
    (tree/make-ref "https://wisen.active-group.de/")

    :else
    (case (tree/node-uri type)
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

      (-> (tree/make-node)
          (tree/node-type type)))))

(defn default-sort-for-predicate [pred]
  (case pred
    "http://schema.org/name"
    tree/literal-string

    "http://schema.org/email"
    tree/literal-string

    "http://schema.org/description"
    tree/literal-string

    "http://schema.org/location"
    (tree/make-node (schema "Place"))

    "http://schema.org/openingHoursSpecification"
    (tree/make-node (schema "OpeningHoursSpecification"))

    "http://schema.org/address"
    (tree/make-node (schema "PostalAddress"))

    "http://schema.org/geo"
    (tree/make-node (schema "GeoCoordinates"))

    tree/literal-string))

(defn default-tree-for-predicate [pred]
  (default-tree-for-sort
   (default-sort-for-predicate pred)))

(defn tree-sort

  ([tree]
    (cond
      (tree/node? tree)
      (tree/node-type tree)

      (tree/ref? tree)
      tree/ref

      (tree/literal-string? tree)
      tree/literal-string

      (tree/literal-decimal? tree)
      tree/literal-decimal

      (tree/literal-boolean? tree)
      tree/literal-boolean
      ))

  ([tree new-sort]
   (if (= new-sort (tree-sort tree))
     tree
     (default-tree-for-sort new-sort))))

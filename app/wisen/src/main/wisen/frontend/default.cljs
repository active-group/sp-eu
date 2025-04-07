(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.schema :as schema]))

(def ^:private lit-s tree/make-literal-string)
(def ^:private lit-d tree/make-literal-decimal)
(def ^:private lit-b tree/make-literal-boolean)

(defn- schema [s]
  (str "http://schema.org/" s))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(defn- make-node [type & props]
  (-> (tree/make-node)
      (tree/node-properties props)
      (tree/node-type (tree/make-node
                       (schema type)))))

(defn- property [pred obj]
  (tree/make-property (schema pred) obj))

(def default-geo-coordinates
  (make-node
   "GeoCoordinates"
   (property "latitude" (lit-s "48.52105844145676"))
   (property "longitude" (lit-s "9.054090697517525"))))

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

(def default-organization
  (make-node
   "Organization"
   (property "name" (lit-s "Name"))
   (property "description" (lit-s "Description"))
   (property "keywords" (lit-s "education, fun, games"))
   (property "location" default-place)
   (property "url" (lit-s "https://..."))
   (property "openingHoursSpecification" default-opening-hours-specification)))

(def default-event
  (make-node
   "Event"
   (property "name" (lit-s "Annual Company Picnic"))
   (property "description" (lit-s "Join us for our annual company picnic with food, games, and fun for the whole family."))
   (property "startDate" (lit-s "2023-06-15T12:00:00-04:00"))
   (property "endDate" (lit-s "2023-06-15T17:00:00-04:00"))
   (property "location" (make-node
                         "Place"
                         (property "name" (lit-s "Company Park"))
                         (property "address" (make-node
                                              "PostalAddress"
                                              (property "streetAddress" (lit-s "123 Main St"))
                                              (property "addressLocality" (lit-s "Anytown"))
                                              (property "addressRegion" (lit-s "CA"))
                                              (property "postalCode" (lit-s "12345"))
                                              (property "addressCountry" (lit-s "USA"))))))
   (property "organizer" (make-node
                          "Organization"
                          (property "name" (lit-s "Acme Corporation"))))
   (property "offers" (make-node
                       "Offer"
                       (property "url" (lit-s "https://example.com/picnic-tickets"))
                       (property "price" (lit-d 25.00))
                       (property "priceCurrency" (lit-s "USD"))
                       (property "availability" (lit-s "https://schema.org/InStock"))))))

(def default-person
  (def person
  (make-node
   "Person"
   (property "name" (lit-s "John Doe"))
   (property "email" (lit-s "john.doe@example.com"))
   (property "telephone" (lit-s "+1-123-456-7890"))
   (property "birthDate" (lit-s "1980-01-01"))
   (property "gender" (lit-s "Male"))
   (property "image" (lit-s "https://example.com/john-doe.jpg"))
   (property "address" (make-node
                        "PostalAddress"
                        (property "streetAddress" (lit-s "123 Main St"))
                        (property "addressLocality" (lit-s "Anytown"))
                        (property "addressRegion" (lit-s "CA"))
                        (property "postalCode" (lit-s "12345"))
                        (property "addressCountry" (lit-s "USA")))))))

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

      "http://schema.org/Event"
      default-event

      "http://schema.org/Person"
      default-person

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

(defn default-tree-for-predicate [schema predicate]
  (case predicate
    "https://wisen.active-group.de/target-group"
    (lit-s "elderly")

    (default-tree-for-sort
     (first
      (schema/sorts-for-predicate schema predicate)))))

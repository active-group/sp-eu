(ns wisen.frontend.default
  (:require [wisen.frontend.tree :as tree]
            [wisen.frontend.schema :as schema]
            [wisen.frontend.value-node :as value-node]))

(def ^:private lit-s tree/make-literal-string)
(def ^:private lit-d tree/make-literal-decimal)
(def ^:private lit-b tree/make-literal-boolean)
(def ^:private lit-t tree/make-literal-time)
(def ^:private lit-date tree/make-literal-date)
(def ^:private lit-datetime tree/make-literal-datetime)

(defn- schema [s]
  (str "http://schema.org/" s))

(def type-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(defn- make-node [type & props]
  (let [props* (conj props
                     (tree/make-property type-uri
                                         (tree/make-node type)))]
    (let [ex (hash [type props])]
      (tree/make-exists
       ex
       (-> (tree/make-node ex)
           (tree/node-properties props*))))))

(defn- make-value [type & props]
  (let [props* (conj props
                     (tree/make-property type-uri
                                         (tree/make-node type)))]
    (tree/make-node (value-node/properties-derive-uri props*) props*)))

(defn- property [pred obj]
  (tree/make-property (schema pred) obj))

(def default-geo-coordinates
  (make-value
   (schema "GeoCoordinates")
   (property "latitude" (lit-d "48.52105844145676"))
   (property "longitude" (lit-d "9.054090697517525"))))

(def default-postal-address
  (make-node
   (schema "PostalAddress")
   (property "streetAddress" (lit-s "Charitéplatz 1"))
   (property "postalCode" (lit-s "10117"))
   (property "addressLocality" (lit-s "Berlin"))
   (property "addressCountry" (lit-s "DE"))))

(def default-opening-hours-specification
  (make-value
   (schema "OpeningHoursSpecification")
   (property "dayOfWeek" (tree/make-node (schema "Monday")))
   (property "opens" (lit-t "10:00:00"))
   (property "closes" (lit-t "17:00:00"))))

(def default-place
  (make-node
   (schema "Place")
   (property "address" default-postal-address)
   (property "openingHoursSpecification" default-opening-hours-specification)))

(def default-organization
  (make-node
   (schema "Organization")
   (property "name" (lit-s "Name"))
   (property "description" (lit-s "Description"))
   (property "keywords" (lit-s "education, fun, games"))
   (property "location" default-place)
   (property "url" (lit-s "https://..."))
   #_(tree/make-property "https://wisen.active-group.de/target-group" (lit-s "elderly"))))

(def default-offer
  (make-node
   (schema "Offer")
   (property "price" (lit-d "7.50"))
   (property "priceCurrency" (lit-s "EUR"))))

(def default-event
  (make-node
   (schema "Event")
   (property "name" (lit-s "Literary Circle"))
   (property "description" (lit-s "We read and discuss various sorts of books together."))
   (property "eventSchedule"
             (make-value (schema "Schedule")
                         (property "byDay"
                                   (-> (tree/make-node (schema "Tuesday"))
                                       (tree/node-type (tree/make-node
                                                        (schema "DayOfWeek")))))
                         (property "startTime"
                                   (lit-t "16:30:00"))))
   (property "eventAttendanceMode" (tree/make-node (schema "OfflineEventAttendanceMode")))
   (property "location" default-place)
   #_(property "organizer" default-organization)
   (property "offers" default-offer)))

(def default-person
  (make-node
   (schema "Person")
   (property "name" (lit-s "John Doe"))
   (property "email" (lit-s "john.doe@example.com"))
   (property "telephone" (lit-s "+1-123-456-7890"))
   (property "birthDate" (lit-date "1980-01-01"))
   (property "address" default-postal-address)))

(defn default-tree-for-sort [type]
  (cond
    (= type tree/literal-string)
    (lit-s "")

    (= type tree/literal-decimal)
    (lit-d "1.0")

    (= type tree/literal-boolean)
    (lit-b true)

    (= type tree/literal-time)
    (lit-t "10:00")

    (= type tree/literal-date)
    (lit-date "2025-01-01")

    (= type tree/literal-datetime)
    (lit-datetime "2025-01-01T10:00:00")

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

      "http://schema.org/DayOfWeek"
      (tree/make-node "http://schema.org/Monday")

      (make-node (tree/node-uri type)))))

(defn default-type-for-predicate [pred]
  (case pred
    "http://schema.org/location"
    (tree/make-node (schema "Place"))

    "http://schema.org/openingHoursSpecification"
    (tree/make-node (schema "OpeningHoursSpecification"))

    "http://schema.org/address"
    (tree/make-node (schema "PostalAddress"))

    "http://schema.org/geo"
    (tree/make-node (schema "GeoCoordinates"))

    (tree/make-node (schema "Thing"))))

(defn default-tree-for-predicate [schema predicate]
  (case predicate
    "https://wisen.active-group.de/target-group"
    (lit-s "elderly")

    "http://schema.org/keywords"
    (lit-s "")

    (default-tree-for-sort
     (first
      (schema/sorts-for-predicate schema predicate)))))

(defn default-node-for-type [type]
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

    (make-node (tree/node-uri type))))

(defn default-tree-for-predicate-and-kind [predicate kind]
  (cond
    (= kind tree/literal-string)
    (lit-s "")

    (= kind tree/literal-decimal)
    (lit-d "1.0")

    (= kind tree/literal-boolean)
    (lit-b true)

    (= kind tree/literal-time)
    (lit-t "10:00")

    (= kind tree/literal-date)
    (lit-date "2025-01-01")

    (= kind tree/literal-datetime)
    (lit-datetime "2025-01-01T10:00:00")

    (= kind tree/ref)
    (tree/make-ref "https://wisen.active-group.de/")

    (= kind tree/node)
    (default-node-for-type
     (default-type-for-predicate predicate))))

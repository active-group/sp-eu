(ns wisen.frontend.schema
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

;; first argument is always a (parsed) rdf graph of the
;; schema (probably always schema.org)

(defn label-for-type [schema type]
  (if-let [res (first
                (rdf/subject-predicate-objects
                 schema
                 (rdf/make-symbol (tree/type-uri type))
                 (rdf/make-symbol "http://www.w3.org/2000/01/rdf-schema#label")))]
    (if (rdf/literal-string? res)
      (rdf/literal-string-value res)
      (tree/type-uri type))
    (tree/type-uri type)))

(defn nice-name [x]
  (case x
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
      "Type"

      "http://schema.org/name"
      "Name"

      "http://schema.org/description"
      "Description"

      "http://schema.org/keywords"
      "Tags"

      "http://schema.org/location"
      "Location"

      "http://schema.org/openingHoursSpecification"
      "Opening Hours"

      "http://schema.org/url"
      "Website"

      "http://schema.org/address"
      "Address"

      "http://schema.org/streetAddress"
      "Street + Nr"

      "http://schema.org/postalCode"
      "Postal code"

      "http://schema.org/addressLocality"
      "Town/region"

      "http://schema.org/addressCountry"
      "Country code"

      "http://schema.org/dayOfWeek"
      "Day of week"

      "http://schema.org/opens"
      "Opens"

      "http://schema.org/closes"
      "Closes"

      nil))

(defn label-for-predicate [schema predicate]
  (if-let [nice (nice-name predicate)]
    nice
    (let [subject
          (first
           (rdf/predicate-object-subjects
            schema
            (rdf/make-symbol "http://www.w3.org/ns/shacl#path")
            (rdf/make-symbol predicate)))

          object
          (first
           (rdf/subject-predicate-objects
            schema
            subject
            (rdf/make-symbol "http://www.w3.org/ns/shacl#name")))]

      (if (rdf/literal-string? object)
        (rdf/literal-string-value object)
        predicate))))

(defn predicates-for-type [schema type]
  (sort
   (concat
    ["http://schema.org/name"
     "http://schema.org/description"
     "http://schema.org/sameAs"
     "http://schema.org/url"
     "http://schema.org/image"
     "http://schema.org/keywords"
     "https://wisen.active-group.de/target-group"]
    (when-let [properties (rdf/subject-predicate-objects
                           schema
                           (rdf/make-symbol (tree/type-uri type))
                           (rdf/make-symbol "http://www.w3.org/ns/shacl#property"))]
      (map (fn [property]
             (rdf/symbol-uri
              (first (rdf/subject-predicate-objects schema property (rdf/make-symbol "http://www.w3.org/ns/shacl#path")))))
           properties)))))

(defn- unpack [sym]
  (assert (rdf/symbol? sym)
          (str "Datatype not a symbol: " (pr-str sym)))
  (case (rdf/symbol-uri sym)
    "http://www.w3.org/2001/XMLSchema#string"
    tree/literal-string

    "http://www.w3.org/2001/XMLSchema#float"
    tree/literal-decimal

    "http://www.w3.org/2001/XMLSchema#boolean"
    tree/literal-boolean

    "http://www.w3.org/2001/XMLSchema#time"
    tree/literal-time

    "http://www.w3.org/2001/XMLSchema#date"
    tree/literal-date

    "http://www.w3.org/ns/shacl#IRI"
    tree/literal-string

    (tree/make-node (rdf/symbol-uri sym))))

(defn- subject-classes [schema subject]
  (rdf/subject-predicate-objects schema subject
                                 (rdf/make-symbol "http://www.w3.org/ns/shacl#class")))

(defn- subject-datatypes [schema subject]
  (rdf/subject-predicate-objects schema subject
                                 (rdf/make-symbol "http://www.w3.org/ns/shacl#datatype")))

(defn- subject-nodekinds [schema subject]
  (rdf/subject-predicate-objects schema subject
                                 (rdf/make-symbol "http://www.w3.org/ns/shacl#nodeKind")))

(defn sorts-for-predicate [schema predicate]
  (assert (some? schema))
  (if-let [subject
           (first
            (rdf/predicate-object-subjects
             schema
             (rdf/make-symbol "http://www.w3.org/ns/shacl#path")
             (rdf/make-symbol predicate)))]
    (let [or-objects
          (rdf/subject-predicate-objects
           schema
           subject
           (rdf/make-symbol "http://www.w3.org/ns/shacl#or"))

          classes-or-datatypes-or-nodekinds
          (let [nodes (mapcat rdf/collection-elements or-objects)]
            (mapcat (fn [node]
                      (concat
                       (subject-classes schema node)
                       (subject-datatypes schema node)
                       (subject-nodekinds schema node)))
                    nodes))

          direct-classes
          (subject-classes schema subject)

          direct-datatypes
          (subject-datatypes schema subject)

          direct-nodekinds
          (subject-nodekinds schema subject)]

      (map unpack (concat classes-or-datatypes-or-nodekinds
                          direct-classes
                          direct-datatypes
                          direct-nodekinds)))
    ;; else: TODO
    [tree/node]
    ))

(defn kinds-for-predicate [schema predicate]
  (distinct
   (map (fn [sort]
          (if (tree/node? sort)
            tree/node
            sort))
        (sorts-for-predicate schema predicate))))

(defn types-for-predicate [schema predicate]
  (mapcat (fn [sort]
            (when (tree/node? sort)
              [sort]))
          (sorts-for-predicate schema predicate)))

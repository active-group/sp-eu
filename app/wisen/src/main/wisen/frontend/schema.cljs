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
                 (rdf/make-symbol (tree/node-uri type))
                 (rdf/make-symbol "http://www.w3.org/2000/01/rdf-schema#label")))]
    (if (rdf/literal-string? res)
      (rdf/literal-string-value res)
      "Unknown type")
    "Unknown type"))

(defn label-for-sort [schema sort]
  (cond
    (= tree/literal-string sort)
    "String"

    (= tree/literal-decimal sort)
    "Decimal"

    (= tree/literal-boolean sort)
    "Boolean"

    :else
    (label-for-type schema sort)))

;;

(defn label-for-predicate [schema predicate]
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
      "Unknown predicate")))

(defn predicates-for-type [schema type]
  (if-let [properties (rdf/subject-predicate-objects
                       schema
                       (rdf/make-symbol (tree/node-uri type))
                       (rdf/make-symbol "http://www.w3.org/ns/shacl#property"))]
    (map (fn [property]
           (rdf/symbol-uri
            (first (rdf/subject-predicate-objects schema property (rdf/make-symbol "http://www.w3.org/ns/shacl#path")))))
         properties)
    ;; TODO default
    []
    ))

(defn- unpack-datatype [datatype]
  (assert (rdf/symbol? datatype)
          (str "Datatype not a symbol: " (pr-str datatype)))
  (case (rdf/symbol-uri datatype)
    "http://www.w3.org/2001/XMLSchema#string"
    tree/literal-string

    "http://www.w3.org/2001/XMLSchema#float"
    tree/literal-decimal

    "http://www.w3.org/2001/XMLSchema#boolean"
    tree/literal-boolean))

(defn sorts-for-predicate [schema predicate]
  (assert (some? schema))
  (let [subject
        (first
         (rdf/predicate-object-subjects
          schema
          (rdf/make-symbol "http://www.w3.org/ns/shacl#path")
          (rdf/make-symbol predicate)))

        objects
        (rdf/subject-predicate-objects
         schema
         subject
         (rdf/make-symbol "http://www.w3.org/ns/shacl#or"))]

    (if (not-empty objects)
      (let [nodes (mapcat rdf/collection-elements objects)]
        (map
         (fn [node]
           (if-let [cls (first (rdf/subject-predicate-objects
                                schema
                                node
                                (rdf/make-symbol "http://www.w3.org/ns/shacl#class")))]
             (do
               (assert (rdf/symbol? cls))
               (tree/make-node
                (rdf/symbol-uri cls)))

             ;; else
             (if-let [datatype (first (rdf/subject-predicate-objects
                                       schema
                                       node
                                       (rdf/make-symbol "http://www.w3.org/ns/shacl#datatype")))]
               (unpack-datatype datatype)
               ;; else
               (if-let [nodekind (first (rdf/subject-predicate-objects
                                         schema
                                         node
                                         (rdf/make-symbol "http://www.w3.org/ns/shacl#nodeKind")))]
                 ;; assume IRI
                 tree/literal-string

                 ;; else
                 (assert false (str "No datatype for node" (pr-str node) " – " (pr-str subject) " – " (pr-str predicate) " – " (pr-str objects))))
               )))
         nodes))

      ;; else, no shacl#or (or at least no cases)
      ;; try for datatype directly
      (let [datatypes (rdf/subject-predicate-objects
                       schema
                       subject
                       (rdf/make-symbol "http://www.w3.org/ns/shacl#datatype"))]
        (map unpack-datatype datatypes)))))

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

    :else
    (label-for-type schema sort)))

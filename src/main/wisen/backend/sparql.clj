(ns wisen.backend.sparql
  (:require [active.data.record :refer [def-record is-a?]])
  (:import (org.apache.jena.query QueryFactory)))

(def place :place)
(def geo-var-name :geo-var-name)
(def latitude-var-name :latitude-var-name)
(def longitude-var-name :longitude-var-name)
(def address :address)
(def address-country-literal-value :address-country-literal-value)
(def address-locality-literal-value :address-locality-literal-value)
(def postal-code-literal-value :postal-code-literal-value)
(def street-address-literal-value :street-address-literal-value)

(def-record variable [variable-name])
(def-record uri [uri-string])

(defn where-triples [e]
  (cond
    (instance? org.apache.jena.sparql.syntax.ElementGroup e)
    ;; A number of graph query elements. Evaluation is a conjunction(AND) of the elements of the groups
    (let [elements (.getElements e)]
      (mapcat where-triples elements))

    (instance? org.apache.jena.sparql.syntax.ElementPathBlock e)
    (.getList
     (.getPattern e))))

(defn- extract-var-name [maybe-old-name x]
  (assert (instance? org.apache.jena.sparql.core.Var x))
  (assert (or (nil? maybe-old-name)
              (= maybe-old-name (.getVarName x))))
  (.getVarName x))

(defn- extract-var-name-or-uri [old x]
  (cond
    (instance? org.apache.jena.sparql.core.Var x)
    (do
      (assert (or (nil? old)
                  (and
                   (is-a? variable old)
                   (= (variable-name old)
                      (.getVarName x)))))
      (variable variable-name (.getVarName x)))

    (instance? org.apache.jena.graph.Node_URI x)
    (do
      (assert (or (nil? old)
                  (and
                   (is-a? uri old)
                   (= (uri-string old) (.toString x)))))
      (uri uri-string (.toString x)))

    :else
    (assert false "Must be Var or Node_URI")
    ))

(defn- extract-literal-value [maybe-old-value x]
  (assert (instance? org.apache.jena.graph.Node_Literal x))
  (let [s (.getLiteralValue x)]
    (assert (instance? java.lang.String s))
    (assert (or (nil? maybe-old-value)
                (= maybe-old-value s)))
    s))

(defn- parse-triple* [m s p o]
  (case p
    "http://schema.org/geo"
    (-> m
        (update place #(extract-var-name-or-uri % s))
        (update geo-var-name #(extract-var-name % o)))

    "http://schema.org/latitude"
    (-> m
        (update geo-var-name #(extract-var-name % s))
        (update latitude-var-name #(extract-var-name % o)))

    "http://schema.org/longitude"
    (-> m
        (update geo-var-name #(extract-var-name % s))
        (update longitude-var-name #(extract-var-name % o)))

    "http://schema.org/address"
    (-> m
        (update place #(extract-var-name-or-uri % s))
        (update address #(extract-var-name-or-uri % o)))

    "http://schema.org/addressCountry"
    (-> m
        (update address #(extract-var-name-or-uri % s))
        (update :address-country-literal-value #(extract-literal-value % o)))

    "http://schema.org/addressLocality"
    (-> m
        (update address #(extract-var-name-or-uri % s))
        (update :address-locality-literal-value #(extract-literal-value % o)))

    "http://schema.org/postalCode"
    (-> m
        (update address #(extract-var-name-or-uri % s))
        (update :postal-code-literal-value #(extract-literal-value % o)))

    "http://schema.org/streetAddress"
    (-> m
        (update address #(extract-var-name-or-uri % s))
        (update :street-address-literal-value #(extract-literal-value % o)))

    "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    m
    ))

(defn- parse-triple [m triple]
  (let [s (.getSubject triple)
        p (.getPredicate triple)
        o (.getObject triple)]
    (assert (instance? org.apache.jena.graph.Node_URI p))
    (parse-triple* m s (.toString p) o)))

(defn- parse-query [q]
  (let [triples (where-triples (.getQueryPattern q))]
    (reduce parse-triple
            {}
            triples)))

(defn parse-query-string [s]
  (let [q (QueryFactory/create s)]
    ;; We can only process SELECT
    (assert (.isSelectType q))

    ;; We can only process SELECT *
    (assert (.isQueryResultStar q))

    (parse-query q)))

(parse-query-string "SELECT * WHERE {
      ?place <http://schema.org/geo> ?coords .
      ?coords <http://schema.org/latitude> ?lat .
      ?coords <http://schema.org/longitude> ?long .

      ?place <http://schema.org/address> ?address .
      ?address <http://schema.org/postalCode> \"72072\" .
      ?address <http://schema.org/addressCountry> \"de\" .
}")

(defn- conj-when-var [var-names var-or-uri]
  (if (is-a? variable var-or-uri)
    (conj var-names (variable-name var-or-uri))
    var-names))

(defn- query-var-names [query]
  (-> [(latitude-var-name query)
       (longitude-var-name query)
       (geo-var-name query)]
      (conj-when-var (place query))
      (conj-when-var (address query))))


#_#_#_#_(sparql/place-var-name query)
       {"type" "uri"
        "value" "http://TODO.org"}

       (sparql/coords-var-name query)
       {"type" "uri"
        "value" "http://TODO.org"}

(defn pack-search-result [query long lat]
  {"head" {"vars" (query-var-names query)}
   "results"
   {"bindings"
    [
     (merge
      {
       (geo-var-name query)
       {"type" "uri"
        "value" "http://TODO.org"}

       (latitude-var-name query)
       {"type" "literal"
        "value" lat}

       (longitude-var-name query)
       {"type" "literal"
        "value" long}})]}})

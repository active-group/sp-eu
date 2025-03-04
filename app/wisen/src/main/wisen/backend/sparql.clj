(ns wisen.backend.sparql
  (:import (org.apache.jena.query ARQ QueryFactory)))

(def place-var-name :place-var-name)
(def coords-var-name :coords-var-name)
(def latitude-var-name :latitude-var-name)
(def longitude-var-name :longitude-var-name)
(def address-var-name :address-var-name)
(def address-country-literal-value :address-country-literal-value)
(def address-locality-literal-value :address-locality-literal-value)
(def postal-code-literal-value :postal-code-literal-value)
(def street-address-literal-value :street-address-literal-value)

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
        (update :place-var-name #(extract-var-name % s))
        (update :coords-var-name #(extract-var-name % o)))

    "http://schema.org/latitude"
    (-> m
        (update :coords-var-name #(extract-var-name % s))
        (update :latitude-var-name #(extract-var-name % o)))

    "http://schema.org/longitude"
    (-> m
        (update :coords-var-name #(extract-var-name % s))
        (update :longitude-var-name #(extract-var-name % o)))

    "http://schema.org/address"
    (-> m
        (update :place-var-name #(extract-var-name % s))
        (update :address-var-name #(extract-var-name % o)))

    "http://schema.org/addressCountry"
    (-> m
        (update :address-var-name #(extract-var-name % s))
        (update :address-country-literal-value #(extract-literal-value % o)))

    "http://schema.org/addressLocality"
    (-> m
        (update :address-var-name #(extract-var-name % s))
        (update :address-locality-literal-value #(extract-literal-value % o)))

    "http://schema.org/postalCode"
    (-> m
        (update :address-var-name #(extract-var-name % s))
        (update :postal-code-literal-value #(extract-literal-value % o)))

    "http://schema.org/streetAddress"
    (-> m
        (update :address-var-name #(extract-var-name % s))
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

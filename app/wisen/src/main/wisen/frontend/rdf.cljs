(ns wisen.frontend.rdf
  (:refer-clojure :exclude [symbol? merge])
  (:require [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as r]
            [active.clojure.lens :as lens]
            ["rdflib" :as rdflib]))

(defn json-ld-string->graph-promise [s]
  (let [g (rdflib/graph)]
    (js/Promise. (fn [resolve! reject!]
                   (rdflib/parse
                    s g
                    "http://example.org/g1"
                    "application/ld+json"
                    (fn [_ graph]
                      (resolve! graph)))))))

(defn json-ld-string->graph-atom [s]
  (let [a (atom nil)]
    (.then (json-ld-string->graph-promise s)
           (fn [g]
             (reset! a g)))
    a))

(defn subjects [graph]
  (set (js->clj (.each ^rdflib/Graph graph js/undefined js/undefined js/undefined))))

(defn subject-predicates [graph subject]
  (set (.each ^rdflib/Graph graph subject js/undefined js/undefined)))

(defn subject-predicate-objects [graph subject predicate]
  (.each ^rdflib/Graph graph subject predicate js/undefined))

(defn subject-objects [graph subject]
  (reduce
   (fn [acc predicate]
     (concat acc (subject-predicate-objects graph subject predicate)))
   #{}
   (subject-predicates graph subject)))

(def-record property
  [property-predicate
   property-object])

(defn subject-properties [graph subject]
  (mapcat
   (fn [predicate]
     (let [objects (subject-predicate-objects graph subject predicate)]
       (map (fn [object]
              (property property-predicate predicate
                        property-object object))
            objects)))
   (subject-predicates graph subject)))

(defn- ingoing [graph node]
  (set (js->clj (.each ^rdflib/Graph graph js/undefined js/undefined node))))

(defn roots [graph]
  (filter
   (fn [node]
     (empty? (ingoing graph node)))
   (subjects graph)))

(defn merge [g1 g2]
  (let [gres (rdflib/graph)]
    (.addAll gres (.-statements g1))
    (.addAll gres (.-statements g2))
    gres))

;; ---

(defn make-symbol [string]
  (rdflib/sym string))

(defn symbol? [x]
  (instance? rdflib/NamedNode x))

(defn symbol-uri [x]
  (.-value x))

(defn make-blank-node []
  (rdflib/BlankNode.))

(defn blank-node? [x]
  (instance? rdflib/BlankNode x))

(defn blank-node-uri [x]
  (.-value x))

(defn node-uri [x]
  (assert (or (symbol? x)
              (blank-node? x)))
  (cond
    (symbol? x)
    (symbol-uri x)

    (blank-node? x)
    (blank-node-uri x)))

(defn make-literal-string [string]
  (rdflib/literal string))

(defn literal-string? [x]
  (instance? rdflib/Literal x))

(defn literal-string-value [x]
  (.-value x))

(defn make-collection [nodes]
  (rdflib/Collection. (clj->js nodes)))

(defn collection? [x]
  (instance? rdflib/Collection x))

(defn collection-elements [x]
  (js->clj (.-elements x)))

(defn node-to-string [x]
  (cond
    (symbol? x)
    (symbol-uri x)

    (blank-node? x)
    "blank"

    (literal-string? x)
    (literal-string-value x)

    (collection? x)
    (pr-str x)))

(defn make-statement [s p o]
  (rdflib/Statement. s p o))

(defn statement? [x]
  (instance? rdflib/Statement x))

;; ---

(defn statements->graph [stmts]
  (let [g (rdflib/graph)]
    (.addAll g (clj->js stmts))
    g))

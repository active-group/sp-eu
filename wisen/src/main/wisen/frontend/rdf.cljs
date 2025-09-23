(ns wisen.frontend.rdf
  (:refer-clojure :exclude [symbol? merge])
  (:require [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as r]
            [active.clojure.lens :as lens]
            [clojure.set :as set]
            [wisen.common.macros :refer-macros [define-rdf-datatype]]
            ["rdflib" :as rdflib])
  (:import goog.object))

(defn json-ld-string->graph-promise [s]
  (let [g (rdflib/graph)]
    (js/Promise. (fn [resolve! reject!]
                   (try
                     ;; rdflib doesn't report errors, so here we try
                     ;; to parse the given string as JSON to at least
                     ;; report if something is wrong in this
                     ;; department
                     (js/JSON.parse s)

                     ;; actual json-ld parsing:
                     (rdflib/parse
                      s g
                      "http://example.org/g1"
                      "application/ld+json"
                      (fn [a graph]
                        (resolve! graph)))
                     (catch js/Error e
                       (reject! e)))))))

(defn json-ld-string->graph-atom [s]
  (let [a (atom nil)]
    (.then (json-ld-string->graph-promise s)
           (fn [g]
             (reset! a g)))
    a))

(defn subjects [graph]
  (set (js->clj (.each ^rdflib/Graph graph js/undefined js/undefined js/undefined))))

(defn- distinct-by [key-fn coll]
  (let [seen (volatile! #{})]
    (filter (fn [x]
              (let [k (key-fn x)]
                (if (contains? @seen k)
                  false
                  (do (vswap! seen conj k)
                      true))))
            coll)))

(defn subject-predicates [graph subject]
  ;; rdflib.js has a bug in that it gives you two! distinct predicates
  ;; for the graph with these two triples: <a> <b> <c>, <a> <b> <d>

  ;; which in itself is not a problem (they are distinct objects, no
  ;; value semantics) but when you then ask for corresponding
  ;; objects (see `subject-predicate-objects`), both of these
  ;; predicates will give you both <c> and <d> each, so if you
  ;; `mapcat` you end up with duplicates.
  (set
   (distinct-by #(.-value %)
                (.each ^rdflib/Graph graph subject js/undefined js/undefined))))

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

(defn predicate-object-subjects [graph predicate object]
  (set (.each ^rdflib/Graph graph js/undefined predicate object)))

(defn- ingoing [graph node]
  (set (js->clj (.each ^rdflib/Graph graph js/undefined js/undefined node))))

(defn roots [graph]
  (filter
   (fn [node]
     (empty? (ingoing graph node)))
   (subjects graph)))

(defn has-outgoing-edges? [graph subj]
  (seq (subject-predicates graph subj)))

(defn get-triples-for-subject [graph subj]
  (let [preds-for-subj (subject-predicates graph subj)]
    (reduce
     (fn [triples pred]
       (let [objects (subject-predicate-objects graph subj pred)]
         (reduce
          (fn [acc obj]
            (conj acc [subj pred obj]))
          triples
          objects)))
     #{}
     preds-for-subj)))

(defn process-object-nodes
  [graph objects acc-visited acc-triples collect-fn]
  (reduce
   (fn [[acc-v acc-t] obj]
     (if (has-outgoing-edges? graph obj)
       (collect-fn obj acc-v acc-t)
       [acc-v acc-t]))  ; Skip recursion for end nodes
   [acc-visited acc-triples]
   objects))

(defn collect-connected-component
  "Takes a starting subject and collects all triples in its connected component"
  [graph s]
  (letfn [(collect-recursive [subj visited triples]
            (if (contains? visited subj)
              [visited triples]  ; Return accumulated results if we've seen this subject
              (let [visited' (conj visited subj)
                    new-triples (get-triples-for-subject graph subj)
                    combined-triples (set/union triples new-triples)
                    objects (distinct (map last new-triples))]
                (process-object-nodes
                 graph
                 objects
                 visited'
                 combined-triples
                 collect-recursive))))]
    (second (collect-recursive s #{} #{}))))

(extend-type rdflib/NamedNode
  cljs.core/IEquiv
  (-equiv [this other]
    (and (instance? rdflib/NamedNode other)
         (= (.-value this) (.-value other)))))

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

(defn node? [x]
  (or (symbol? x)
      (blank-node? x)))

;;

(extend-type rdflib/Literal
  cljs.core/IEquiv
  (-equiv [this other]
    (and (instance? rdflib/Literal other)
         (= (.-value this) (.-value other))
         (= (.-datatype this) (.-datatype other)))))

(define-rdf-datatype xsd-string
  "http://www.w3.org/2001/XMLSchema#string"
  make-literal-string
  literal-string?
  literal-string-value)

(define-rdf-datatype xsd-decimal
  "http://www.w3.org/2001/XMLSchema#decimal"
  make-literal-decimal
  literal-decimal?
  literal-decimal-value)

(define-rdf-datatype xsd-boolean
  "http://www.w3.org/2001/XMLSchema#boolean"
  make-literal-boolean
  literal-boolean?
  literal-boolean-value)

(define-rdf-datatype xsd-time
  "http://www.w3.org/2001/XMLSchema#time"
  make-literal-time
  literal-time?
  literal-time-value)

(define-rdf-datatype xsd-date
  "http://www.w3.org/2001/XMLSchema#date"
  make-literal-date
  literal-date?
  literal-date-value)

;;

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

    (literal-decimal? x)
    (literal-decimal-value x)

    (literal-boolean? x)
    (literal-boolean-value x)

    (literal-time? x)
    (literal-time-value x)

    (literal-date? x)
    (literal-date-value x)

    (collection? x)
    (pr-str x)))

(extend-type rdflib/Statement
  cljs.core/IEquiv
  (-equiv [this other]
    (and (instance? rdflib/Statement other)
         (= (.-subject this) (.-subject other))
         (= (.-predicate this) (.-predicate other))
         (= (.-object this) (.-object other))
         ;; TODO: is this relevant for us?
         #_(= (.-graph this) (.-graph other)))))

(defn make-statement [s p o]
  (rdflib/Statement. s p o))

(defn statement? [x]
  (instance? rdflib/Statement x))

;; ---

(defn graph->statements [g]
  (set
   (js->clj (.-statements g))))

(defn statements->graph [stmts]
  (let [g (rdflib/graph)]
    (.addAll g (clj->js stmts))
    g))

;; ---

(defn get-subcomponents
  "Collects connected subcomponents of a graph"
  [graph]
  (let [root-nodes (roots graph)
        subcomponents-triples (map #(collect-connected-component graph %)
                                   root-nodes)
        subcomponents-stms (mapv (fn [triples]
                                   (mapv (fn [triple]
                                           (apply make-statement triple))
                                         triples))
                                 subcomponents-triples)
        ]
    (map statements->graph subcomponents-stms)))
;; ---



(defn- schmontains? [coll x]
  (reduce (fn [acc y]
            (if (= y x)
              (reduced true)
              acc))
          false
          coll))

(defn- schmunion
  "Union two sets with `=` as equivalence."
  [s1 s2]
  (reduce
   (fn [acc x]
     (if (schmontains? acc x)
       acc
       (conj acc x)))
   s1
   s2))

(defn merge [g1 g2]
  (statements->graph
   (schmunion
    (graph->statements g1)
    (graph->statements g2))))

;; ---

(defn geo-positions [graph]
  (let [sparql (str "SELECT ?lat, ?long WHERE { ?s <http://schema.org/latitude> ?lat . ?s <http://schema.org/longitude> ?long . }")
        q (rdflib/SPARQLToQuery sparql false graph)
        results (.querySync ^rdflib/IndexedFormula graph q)
        values (atom [])]

    (.forEach results
              (fn [result]
                (let [lat (aget result "?lat")
                      long (aget result "?long")]
                  (swap! values conj [lat long])
                  )))
    @values))

(ns wisen.frontend.tree
  "Tools to turn arbitrary RDF graphs into trees with references."
  (:refer-clojure :exclude [uri?])
  (:require [wisen.frontend.rdf :as rdf]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(declare tree)

(def URI realm/string)

(defn make-uri [s]
  s)

(defn uri? [s]
  (string? s))

(defn uri-string [uri]
  uri)

(def-record ref [ref-uri :- URI])

(defn ref? [x]
  (record/is-a? ref x))

(def-record literal-string
  [literal-string-value :- realm/string])

(defn literal-string? [x]
  (record/is-a? literal-string x))

(def-record property
  [property-predicate :- URI
   property-object :- (realm/delay tree)
   ])

(def-record node [node-uri :- URI
                  node-properties :- (realm/sequence-of property)])

(defn node-object-for-predicate [predicate]
  (fn
    ([node]
     (some (fn [prop]
             (when (= (property-predicate prop) predicate)
               (property-object prop)))
           (node-properties node)))
    ([node new-object]
     (lens/overhaul node node-properties
                    (fn [props]
                      (map (fn [prop]
                             (if (= (property-predicate prop) predicate)
                               (property-object prop new-object)
                               prop))
                           props))))))

(defn node? [x]
  (record/is-a? node x))

(def tree (realm/union
           ref
           literal-string
           node))

;; The following just does a simple walk through the graph with a `visited` set as context.

(defn- node->tree [graph links x]
  (cond
    (rdf/symbol? x)
    (let [uri (rdf/symbol-uri x)]
      (if-let [link (get links uri)]
        [links (ref ref-uri
                    link)]
        (let [links* (conj links uri)
              [links** props] (reduce (fn [[links props] prop]
                                        (let [[links* tree] (node->tree graph links (rdf/property-object prop))]
                                          [links* (conj props (property property-predicate (rdf/symbol-uri
                                                                                            (rdf/property-predicate prop))
                                                                        property-object tree))]))
                                      [links* []]
                                      (rdf/subject-properties graph x))]
          [links** (node node-uri uri
                         node-properties props)])))

    (rdf/literal-string? x)
    [links (literal-string literal-string-value
                           (rdf/literal-string-value x))]

    (rdf/collection? x)
    (assert false "Not supported yet")))

(defn graph->trees [g]
  (second
   (reduce
    (fn [[links trees] x]
      (let [[links* tree] (node->tree g links x)]
        [links* (conj trees tree)]))
    [#{} []]
    (rdf/roots g))))

(defn- tree->statements [statements tree]
  (cond
    (record/is-a? ref tree)
    statements

    (record/is-a? literal-string tree)
    statements

    (record/is-a? node tree)
    (reduce (fn [statements prop]
              (let [pred (property-predicate prop)
                    obj (property-object prop)]
                (cond
                  (record/is-a? ref obj)
                  (conj statements (rdf/make-statement (rdf/make-symbol (node-uri tree))
                                                       (rdf/make-symbol pred)
                                                       (rdf/make-symbol (ref-uri obj))))

                  (record/is-a? literal-string obj)
                  (conj statements (rdf/make-statement (rdf/make-symbol (node-uri tree))
                                                       (rdf/make-symbol pred)
                                                       (rdf/make-literal-string (literal-string-value obj))))

                  (record/is-a? node obj)
                  (let [statements* (tree->statements statements obj)]
                    (conj statements* (rdf/make-statement (rdf/make-symbol (node-uri tree))
                                                          (rdf/make-symbol pred)
                                                          (rdf/make-symbol (node-uri obj))))))))
            statements
            (node-properties tree))))

(defn- trees->statements [trees]
  (reduce tree->statements [] trees))

(defn trees->graph [trees]
  (rdf/statements->graph (trees->statements trees)))

(def graph<->trees
  (lens/xmap graph->trees
             trees->graph))

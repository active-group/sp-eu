(ns wisen.frontend.tree
  "Tools to turn arbitrary RDF graphs into trees with references."
  (:refer-clojure :exclude [uri?])
  (:require [wisen.frontend.rdf :as rdf]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [wisen.common.prefix :as prefix]))

(declare tree)

(def URI realm/string)

(defn make-uri [s]
  s)

(defn uri? [s]
  (string? s))

(defn uri-string [uri]
  uri)

(def-record ref [ref-uri :- URI])

(defn make-ref [uri]
  (ref ref-uri uri))

(defn ref? [x]
  (record/is-a? ref x))

;;

(def-record literal-string
  [literal-string-value :- realm/string])

(defn make-literal-string [s]
  (literal-string literal-string-value s))

(defn literal-string? [x]
  (record/is-a? literal-string x))

;;

(def-record literal-decimal
  [literal-decimal-value #_#_:- realm/number])

(defn make-literal-decimal [s]
  (literal-decimal literal-decimal-value s))

(defn literal-decimal? [x]
  (record/is-a? literal-decimal x))

;;

(def-record literal-boolean
  [literal-boolean-value :- realm/string])

(defn make-literal-boolean [s]
  (literal-boolean literal-boolean-value s))

(defn literal-boolean? [x]
  (record/is-a? literal-boolean x))

;;

(defn primitive? [x]
  (or (literal-string? x)
      (literal-decimal? x)
      (literal-boolean? x)
      (ref? x)))

;;

(def-record property
  [property-predicate :- URI
   property-object :- (realm/delay tree)
   ])

(defn make-property [pred obj]
  (property property-predicate pred
            property-object obj))

(def-record node [node-uri :- URI
                  node-properties :- (realm/sequence-of property)])

;; TODO: this is a bad side-effect that leads to a lot of suffering down the road:
(defn- fresh-uri! []
  (prefix/resource (random-uuid)))

(defn make-node
  ([]
   (node node-uri (fresh-uri!)
         node-properties []))
  ([uri]
   (node node-uri uri
         node-properties []))
  ([uri properties]
   (node node-uri uri
         node-properties properties)))

(defn node-objects-for-predicate [node predicate]
  (reduce (fn [acc prop]
            (if (= (property-predicate prop) predicate)
              (conj acc (property-object prop))
              acc))
          []
          (node-properties node)))

(defn node-assoc [node predicate obj]
  (lens/overhaul node
                 node-properties
                 conj
                 (make-property predicate obj)))

(defn node-assoc-replace [node predicate obj]
  (let [new-property (make-property predicate obj)]
    (lens/overhaul node
                   node-properties
                   (fn [properties]
                     (let [[did-replace? new-properties]
                           (reduce (fn [[did-replace? properties] property]
                                     (if did-replace?
                                       ;; move on
                                       [true (conj properties property)]
                                       (if (= (property-predicate property)
                                              predicate)
                                         ;; replace
                                         [true (conj properties new-property)]
                                         ;; move on
                                         [false (conj properties property)])))
                                   [false []]
                                   properties)]
                       (if did-replace?
                         ;; done
                         new-properties
                         ;; no replaced, just conj
                         (conj new-properties new-property)))))))

(defn node-object-for-predicate [predicate]
  (fn
    ([node]
     (some (fn [prop]
             (when (= (property-predicate prop) predicate)
               (property-object prop)))
           (node-properties node)))
    ([node new-object]
     (node-assoc-replace node predicate new-object))))

(defn node? [x]
  (record/is-a? node x))

(def tree (realm/union
           ref
           literal-string
           literal-decimal
           literal-boolean
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
    [links (make-literal-string (rdf/literal-string-value x))]

    (rdf/literal-decimal? x)
    [links (make-literal-decimal (rdf/literal-decimal-value x))]

    (rdf/literal-boolean? x)
    [links (make-literal-boolean (rdf/literal-boolean-value x))]

    (rdf/collection? x)
    (assert false "Not supported yet")))

(defn graph->trees [g]
  (second
   (reduce
    (fn [[links trees] x]
      (let [[links* tree] (node->tree g links x)]
        [links* (conj trees tree)]))
    [#{} []]
    ;; TODO: we shouldn't assume that there are roots. e.g. A -> B, B
    ;; -> A has no roots.  rather look for "basis", a minimal set of
    ;; nodes from which every other root is reachable
    (rdf/roots g))))

(defn tree-handle [obj]
  (cond
    (record/is-a? ref obj)
    (ref-uri obj)

    (record/is-a? literal-string obj)
    (literal-string-value obj)

    (record/is-a? literal-decimal obj)
    (literal-decimal-value obj)

    (record/is-a? literal-boolean obj)
    (literal-boolean-value obj)

    (record/is-a? node obj)
    (node-uri obj)))

(defn- tree->statements [statements tree]
  (cond
    (record/is-a? ref tree)
    statements

    (record/is-a? literal-string tree)
    statements

    (record/is-a? literal-decimal tree)
    statements

    (record/is-a? literal-boolean tree)
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

                  (record/is-a? literal-decimal obj)
                  (conj statements (rdf/make-statement (rdf/make-symbol (node-uri tree))
                                                       (rdf/make-symbol pred)
                                                       (rdf/make-literal-decimal (literal-decimal-value obj))))

                  (record/is-a? literal-boolean obj)
                  (conj statements (rdf/make-statement (rdf/make-symbol (node-uri tree))
                                                       (rdf/make-symbol pred)
                                                       (rdf/make-literal-boolean (literal-boolean-value obj))))

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

;; schema.org specific

(def node-type
  (lens/>>
   (node-object-for-predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
   (lens/or-else (make-node "http://schema.org/Thing"))))

(defn type-uri [type]
  (cond
    (node? type)
    (node-uri type)

    (ref? type)
    (ref-uri type)))

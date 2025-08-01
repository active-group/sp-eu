(ns wisen.frontend.tree
  "Tools to turn arbitrary RDF graphs into trees with references."
  (:refer-clojure :exclude [uri? exists?])
  (:require [wisen.frontend.rdf :as rdf]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.functions :as f]
            [active.clojure.lens :as lens]
            [wisen.common.prefix :as prefix]
            [wisen.frontend.existential :as existential]
            [wisen.common.fnplus :refer-macros [fn+]]
            ["rdflib" :as rdflib]))

(declare tree)

(def URI (realm/union
          ;; a global identifier
          realm/string
          ;; a local identifier, bound by a surrounding `exists`
          existential/existential))

(defn make-uri [s]
  s)

(defn uri? [s]
  (string? s))

(defn uri-string [uri]
  (if (existential/existential? uri)
    (str uri)
    uri))

(def-record ref [ref-uri :- URI])

(defn make-ref [uri]
  (ref ref-uri uri))

(defn ref? [x]
  (record/is-a? ref x))

;;

(def-record exists
  [exists-existential :- existential/existential
   exists-tree :- tree
   ])

(defn make-exists
  ([ex tree]
   (exists exists-existential ex
           exists-tree tree)))

(defn exists? [x]
  (record/is-a? exists x))

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
  [literal-boolean-value :- realm/boolean])

(defn make-literal-boolean [s]
  (literal-boolean literal-boolean-value s))

(defn literal-boolean? [x]
  (record/is-a? literal-boolean x))

;;

(def-record literal-time
  [literal-time-value :- realm/string])

(defn make-literal-time [s]
  (literal-time literal-time-value s))

(defn literal-time? [x]
  (record/is-a? literal-time x))

;;

(def-record literal-date
  [literal-date-value :- realm/string])

(defn make-literal-date [s]
  (literal-date literal-date-value s))

(defn literal-date? [x]
  (record/is-a? literal-date x))

;;

(def-record property
  [property-predicate :- URI
   property-object #_#_:- (realm/delay tree)
   ])

(defn make-property [pred obj]
  (property property-predicate pred
            property-object obj))

(def-record node [node-uri :- URI
                  node-properties :- (realm/sequence-of property)])

(defn make-node
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

(def-record many
  [many-trees :- (realm/sequence-of (realm/delay tree))])

(defn make-many [ts]
  (many many-trees ts))

(defn many? [x]
  (record/is-a? many x))

(def tree (realm/union
           ref
           literal-string
           literal-decimal
           literal-boolean
           literal-time
           literal-date
           node
           many
           exists))

;; The following just does a simple walk through the graph with a `visited` set as context.

(defn- node->tree* [graph links existentials x]
  (cond
    (rdf/node? x)
    (let [raw-uri (rdf/symbol-uri x)
          uri (if (rdf/blank-node? x)
                (existential/coerce-existential raw-uri)
                raw-uri)
          existentials* (if (rdf/blank-node? x)
                          (conj existentials uri)
                          existentials)]
      (if-let [link (get links uri)]
        [links existentials* (ref ref-uri link)]
        (let [links*
              (conj links uri)

              [links** existentials** props]
              (reduce (fn [[links existentials props] prop]
                        (let [[links* existentials* tree] (node->tree* graph links existentials (rdf/property-object prop))]
                          [links* existentials* (conj props (property property-predicate (rdf/symbol-uri
                                                                                          (rdf/property-predicate prop))
                                                                      property-object tree))]))
                      [links* existentials* []]
                      (rdf/subject-properties graph x))]
          [links** existentials** (node node-uri uri
                                        node-properties props)])))

    (rdf/literal-string? x)
    [links existentials (make-literal-string (rdf/literal-string-value x))]

    (rdf/literal-decimal? x)
    [links existentials (make-literal-decimal (rdf/literal-decimal-value x))]

    (rdf/literal-boolean? x)
    (let [raw-bool (rdf/literal-boolean-value x)
          parsed-bool (cond
                        (string? raw-bool)
                        (case raw-bool
                          "1" true
                          "true" true
                          "0" false
                          "false" false)

                        (boolean? raw-bool)
                        raw-bool

                        :else
                        false)]
      [links existentials (make-literal-boolean parsed-bool)])

    (rdf/literal-date? x)
    [links existentials (make-literal-date (rdf/literal-date-value x))]

    (rdf/literal-time? x)
    [links existentials (make-literal-time (rdf/literal-time-value x))]

    (rdf/collection? x)
    (let [[links* existentials* trees] (reduce (fn [[links existentials trees] node]
                                                 (let [[links* existentials* tree] (node->tree* graph links existentials node)]
                                                   [links* existentials* (conj trees tree)]))
                                               [links existentials []]
                                               (rdf/collection-elements x))]
      [links* (many many-trees trees)])

    ;; fallback: raw literal, we coerce to string
    (instance? rdflib/Literal x)
    [links existentials (make-literal-string (str (.-value x)))]
    ))

(defn- node->tree [graph links existentials x]
  (node->tree* graph links existentials x))

(defn- wrap-ex [tree ex]
  (make-exists ex tree))

(defn graph->tree [g]
  (let [[_links existentials trees]
        (reduce
         (fn [[links existentials trees] x]
           (let [[links* existentials* tree] (node->tree g links existentials x)]
             [links* existentials* (conj trees tree)]))
         [#{} [] []]
         ;; TODO: we shouldn't assume that there are roots. e.g. A -> B, B
         ;; -> A has no roots.  rather look for "basis", a minimal set of
         ;; nodes from which every other root is reachable
         (rdf/roots g))]
    (reduce wrap-ex
            (if (= 1 (count trees))
              (first trees)
              (many many-trees trees))
            existentials)))

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

(defn tree-properties [tree]
  (cond
    (many? tree)
    (mapcat tree-properties (many-trees tree))

    (exists? tree)
    (tree-properties (exists-tree tree))

    (ref? tree)
    []

    (literal-string? tree)
    []

    (literal-decimal? tree)
    []

    (literal-boolean? tree)
    []

    (literal-time? tree)
    []

    (literal-date? tree)
    []

    (node? tree)
    (node-properties tree)))

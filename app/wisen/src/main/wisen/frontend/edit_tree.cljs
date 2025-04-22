(ns wisen.frontend.edit-tree
  "Turn a rooted tree (wisen.frontend.tree) into an edit tree, tracking changes"
  (:refer-clojure :exclude [exists?])
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [wisen.frontend.change :as change]
            [wisen.frontend.util :as util]))

(def-record deleted
  [deleted-original-value])

(defn make-deleted [orig]
  (deleted deleted-original-value orig))

(defn deleted? [x]
  (is-a? deleted x))

(def-record added
  [added-result-value])

(defn make-added [x]
  (added added-result-value x))

(defn added? [x]
  (is-a? added x))

(def-record maybe-changed
  [maybe-changed-original-value
   maybe-changed-result-value])

(defn make-maybe-changed [orig res]
  (maybe-changed maybe-changed-original-value orig
                 maybe-changed-result-value res))

(defn maybe-changed? [x]
  (is-a? maybe-changed x))

(defn- make-same [x]
  (maybe-changed
   maybe-changed-original-value x
   maybe-changed-result-value x))

(declare edit-tree-handles)

(defn same? [x]
  (and (is-a? maybe-changed x)
       (= (set (edit-tree-handles (maybe-changed-original-value x)))
          (set (edit-tree-handles (maybe-changed-result-value x))))))

(defn changed? [x]
  (and (is-a? maybe-changed x)
       (not (same? x))))

(defn can-delete? [x]
  (is-a? maybe-changed x))

(defn mark-deleted [x]
  (assert (is-a? maybe-changed x) "Can only mark deleted maybe-changed")
  (deleted
   deleted-original-value
   (maybe-changed-original-value x)))

(def marked (realm/union
             deleted
             added
             maybe-changed))

(defn marked-current [m]
  (cond
    (is-a? maybe-changed m)
    (maybe-changed-result-value m)

    (is-a? deleted m)
    (deleted-original-value m)

    (is-a? added m)
    (added-result-value m)))

(defn marked-result-value
  ([m]
   (cond
     (is-a? maybe-changed m)
     (maybe-changed-result-value m)

     (is-a? deleted m)
     nil

     (is-a? added m)
     (added-result-value m)))
  ([m v]
   (cond
     (is-a? maybe-changed m)
     (maybe-changed-result-value m v)

     (is-a? deleted m)
     m

     (is-a? added m)
     (added-result-value m v))))

;; ---

(declare edit-tree)

(def-record edit-node
  [edit-node-uri :- tree/URI
   edit-node-properties :- (realm/map-of tree/URI ; predicate
                                         (realm/sequence-of
                                          ;; payload: edit-tree
                                          marked))
   edit-node-focused? :- (realm/optional realm/boolean)])

(defn edit-node? [x]
  (is-a? edit-node x))

;;

(def-record many
  [many-edit-trees :- (realm/sequence-of (realm/delay edit-tree))
   many-focused? :- (realm/optional realm/boolean)])

(defn many? [x]
  (is-a? many x))

;;

(def-record exists
  [exists-edit-tree :- (realm/delay edit-tree)])

(defn exists? [x]
  (is-a? exists x))

;;

(def-record ref
  [ref-uri :- tree/URI
   ref-focused? :- realm/boolean])

(defn make-ref
  ([uri]
   (make-ref uri false))
  ([uri focused?]
   (ref ref-uri uri ref-focused? focused?)))

(defn ref? [x]
  (is-a? ref x))

;;

(def-record literal-string
  [literal-string-value :- realm/string
   literal-string-focused? :- realm/boolean])

(defn make-literal-string
  ([s]
   (literal-string literal-string-value s
                   literal-string-focused? false))
  ([s focused?]
   (literal-string literal-string-value s
                   literal-string-focused? focused?)))

(defn literal-string? [x]
  (is-a? literal-string x))

;;

(def-record literal-decimal
  [literal-decimal-value :- realm/string
   literal-decimal-focused? :- realm/boolean])

(defn make-literal-decimal
  ([s]
   (literal-decimal literal-decimal-value s
                    literal-decimal-focused? false))
  ([s focused?]
   (literal-decimal literal-decimal-value s
                    literal-decimal-focused? focused?)))

(defn literal-decimal? [x]
  (is-a? literal-decimal x))

;;

(def-record literal-boolean
  [literal-boolean-value :- realm/boolean
   literal-boolean-focused? :- realm/boolean])

(defn make-literal-boolean
  ([s]
   (literal-boolean literal-boolean-value s
                    literal-boolean-focused? false))
  ([s focused?]
   (literal-boolean literal-boolean-value s
                    literal-boolean-focused? focused?)))

(defn literal-boolean? [x]
  (is-a? literal-boolean x))

;;

(def edit-tree (realm/union
                ref
                literal-string
                literal-decimal
                literal-boolean
                exists
                edit-node))

(def edit-tree-focused?
  (util/cond-lens
   [ref ref-focused?]
   [literal-string literal-string-focused?]
   [literal-decimal literal-decimal-focused?]
   [literal-boolean literal-boolean-focused?]
   [many many-focused?]
   [edit-node edit-node-focused?]))

(defn focus
  "Heuristics to focus on an edit tree."
  [etree]
  (cond
    (ref? etree)
    (edit-tree-focused? etree true)

    (literal-string? etree)
    (edit-tree-focused? etree true)

    (literal-decimal? etree)
    (edit-tree-focused? etree true)

    (literal-boolean? etree)
    (edit-tree-focused? etree true)

    (is-a? many etree)
    (lens/overhaul etree (lens/>> many-edit-trees lens/first) focus)

    (is-a? edit-node etree)
    ;; focus the left-most neighbour
    (let [eprops (edit-node-properties etree)
          [eprops* focused?]
          (reduce (fn [[props* focused?] [pred metrees]]
                    (if focused?
                      [(assoc props* pred metrees) true]
                      (let [[metrees* focused?]
                            (reduce (fn [[metrees* focused?] metree]
                                      (if focused?
                                        [(conj metrees* metree) true]
                                        (cond
                                          (is-a? maybe-changed metree)
                                          [(conj metrees*
                                                 (lens/overhaul metree maybe-changed-result-value focus))
                                           true]

                                          (is-a? deleted metree)
                                          [(conj metrees* metree) false]

                                          (is-a? added metree)
                                          [(conj metrees*
                                                 (lens/overhaul metree added-result-value focus))
                                           true]))
                                      )
                                    []
                                    metrees)]
                        [(assoc props* pred metrees*) focused?])))
                  [{} false]
                  eprops)]
      (if focused?
        (edit-node-properties etree eprops*)
        ;; else focus on uri
        (edit-node-focused? etree true)))))

(declare edit-tree-original)

(defn- make-edit-tree [tree cns]
  (cond
    (tree/many? tree)
    (lens/overhaul tree tree/many-trees
                   (fn [trees]
                     (map #(make-edit-tree % cns) trees)))

    (tree/exists? tree)
    (exists
     exists-edit-tree
     (make-edit-tree (tree/exists-tree tree) cns))

    (tree/ref? tree)
    (make-ref (tree/ref-uri tree))

    (tree/literal-string? tree)
    (make-literal-string (tree/literal-string-value tree))

    (tree/literal-decimal? tree)
    (make-literal-decimal (tree/literal-decimal-value tree))

    (tree/literal-boolean? tree)
    (make-literal-boolean (tree/literal-boolean-value tree))

    (tree/node? tree)
    (edit-node
     edit-node-uri (tree/node-uri tree)
     edit-node-properties (reduce (fn [eprops prop]
                                    (update eprops
                                            (tree/property-predicate prop)
                                            conj
                                            (cns
                                             (make-edit-tree
                                              (tree/property-object prop)
                                              cns))))
                                  {}
                                  (tree/node-properties tree)))))

(defn make-added-edit-tree [tree]
  (make-edit-tree tree make-added))

(defn make-same-edit-tree [tree]
  (make-edit-tree tree make-same))

(declare edit-tree-result-tree)

(defn- edit-node-result-node [enode]
  (tree/make-node
   (edit-node-uri enode)
   (reduce (fn [props [pred metrees]]
             (let [trees (mapcat (fn [metree]
                                   (cond
                                     (is-a? maybe-changed metree)
                                     [(edit-tree-result-tree
                                       (maybe-changed-result-value metree))]

                                     (is-a? added metree)
                                     [(edit-tree-result-tree
                                       (added-result-value metree))]

                                     (is-a? deleted metree)
                                     []))
                                 metrees)]
               (if-not (empty? trees)
                 (concat props
                         (map (partial tree/make-property pred)
                              trees))
                 props)))
           []
           (edit-node-properties enode))))

(defn edit-tree-result-tree [etree]
  (cond
    (ref? etree)
    (tree/make-ref (ref-uri etree))

    (literal-string? etree)
    (tree/make-literal-string (literal-string-value etree))

    (literal-decimal? etree)
    (tree/make-literal-decimal (literal-decimal-value etree))

    (literal-boolean? etree)
    (tree/make-literal-boolean (literal-boolean-value etree))

    (is-a? many etree)
    (tree/make-many (map edit-tree-result-tree (many-edit-trees etree)))

    (is-a? edit-node etree)
    (edit-node-result-node etree)))

(defn set-edit-node-original [enode new-node]
  (assert (empty? (edit-node-properties enode)))
  (edit-node-properties enode
                        (edit-node-properties (make-edit-tree new-node make-same))))

(defn edit-node-add-property [enode predicate object-tree]
  (lens/overhaul enode edit-node-properties
                 (fn [eprops]
                   (update eprops
                           predicate
                           conj
                           (make-added (focus
                                        (make-added-edit-tree object-tree)))))))

(defn edit-tree-changeset [etree]
  (cond
    (ref? etree)
    []

    (literal-string? etree)
    []

    (literal-decimal? etree)
    []

    (literal-boolean? etree)
    []

    (many? etree)
    (apply concat
           (map edit-tree-changeset
                (many-edit-trees etree)))

    (exists? etree)
    (let [changes* (edit-tree-changeset (exists-edit-tree etree))]
      [(change/make-with-blank-node changes*)])

    (is-a? edit-node etree)
    (let [subject (edit-node-uri etree)]
      (mapcat
       (fn [[predicate metrees]]
         (mapcat (fn [metree]
                   (cond
                     (is-a? maybe-changed metree)
                     (concat
                      ;; changes on before tree
                      (edit-tree-changeset
                       (maybe-changed-original-value metree))

                      ;; changes here
                      (let [objects (edit-tree-handles
                                     (maybe-changed-original-value metree))]
                        (if (changed? metree)
                          (concat
                           ;; remove original
                           (mapcat
                            (fn [object]
                              [(change/make-delete
                                (change/make-statement subject predicate object))])
                            (edit-tree-handles
                             (maybe-changed-original-value metree)))
                           ;; add result
                           (mapcat
                            (fn [object]
                              [(change/make-add
                                  (change/make-statement subject predicate object))])
                            (edit-tree-handles
                             (maybe-changed-result-value metree))))
                          
                          []))

                      ;; changes on after tree
                      (edit-tree-changeset
                       (maybe-changed-result-value metree)))

                     (is-a? added metree)
                     (concat
                      (edit-tree-changeset
                       (added-result-value metree))
                      (map
                       (fn [object]
                         (change/make-add
                          (change/make-statement subject predicate object)))
                       (edit-tree-handles
                        (added-result-value metree))))

                     (is-a? deleted metree)
                     (concat
                      (edit-tree-changeset
                       (deleted-original-value metree))
                      (map
                       (fn [object]
                         (change/make-delete
                          (change/make-statement subject predicate object)))
                       (edit-tree-handles
                        (deleted-original-value metree))))))

                 metrees))

       (edit-node-properties etree)))))

(defn edit-tree-commit-changes
  [etree]
  (cond
    (ref? etree)
    etree

    (literal-string? etree)
    etree

    (literal-decimal? etree)
    etree

    (literal-boolean? etree)
    etree

    (many? etree)
    (lens/overhaul etree many-edit-trees
                   #(map edit-tree-commit-changes %))

    (is-a? edit-node etree)
    (let [subject (edit-node-uri etree)]
      (edit-node-properties
       etree
       (reduce (fn [props* [predicate metrees]]
                 (assoc props* predicate
                        (mapcat
                         (fn [metree]
                           (cond
                             (is-a? maybe-changed metree)
                             [(make-same (edit-tree-commit-changes
                                          (maybe-changed-result-value metree)))]

                             (is-a? added metree)
                             [(make-same (edit-tree-commit-changes
                                          (added-result-value metree)))]

                             (is-a? deleted metree)
                             []))
                         metrees)
                        ))
               {}
               (edit-node-properties etree))))))

(defn- edit-tree-handles [etree]
  (cond
    (ref? etree)
    [(tree/make-ref (ref-uri etree))]

    (literal-string? etree)
    [(tree/make-literal-string (literal-string-value etree))]

    (literal-decimal? etree)
    [(tree/make-literal-decimal (literal-decimal-value etree))]

    (literal-boolean? etree)
    [(tree/make-literal-boolean (literal-boolean-value etree))]

    (many? etree)
    (mapcat edit-tree-handles (many-edit-trees etree))

    (is-a? edit-node etree)
    [(edit-node-uri etree)]))

(defn can-discard-edit? [x]
  (or (changed? x)
      (is-a? deleted x)
      (is-a? added x)))

(letfn [(vec-remove
          [pos coll]
          (into (subvec coll 0 pos) (subvec coll (inc pos))))]

  (defn discard-edit [node predicate idx]
    (lens/overhaul node
                   edit-node-properties
                   (fn [eprops]
                     (let [metrees (get eprops predicate)
                           metree (nth metrees idx)
                           result-metrees
                           (cond
                             (is-a? maybe-changed metree)
                             (assoc (vec metrees)
                                    idx
                                    (make-same (maybe-changed-original-value metree)))

                             (is-a? deleted metree)
                             (assoc (vec metrees)
                                    idx
                                    (make-same (deleted-original-value metree)))

                             (is-a? added metree)
                             (vec-remove idx (vec metrees)))]
                       (if (empty? result-metrees)
                         (dissoc eprops predicate)
                         (assoc eprops predicate result-metrees)))))))

(defn can-refresh? [etree]
  (and (is-a? edit-node etree)
       (empty? (edit-node-properties etree))))

;; re-implementations of wisen.frontend.tree stuff

(def node? (partial is-a? edit-node))

(def node-uri edit-node-uri)

(defn tree-uri
  ([etree]
   (cond
     (is-a? edit-node etree)
     (edit-node-uri etree)

     (ref? etree)
     (ref-uri etree)))

  ([etree uri]
   (cond
     (is-a? edit-node etree)
     (edit-node-uri etree uri)

     (ref? etree)
     (ref-uri etree uri))))

(defn node-type [enode]
  (tree/node-type (edit-tree-result-tree enode)))

(defn graph->edit-tree [graph]
  (make-same-edit-tree (tree/graph->tree graph)))

(defn node-object-for-predicate [pred enode]
  (let [metrees (get (edit-node-properties enode) pred)]
    (marked-current (first metrees))))

(defn node-assoc-replace [enode pred etree]
  (-> enode
      (lens/overhaul edit-node-properties
                     (fn [old-props]
                       (reduce (fn [new-props [pred* metrees]]
                                 (if (= pred pred*)
                                   (let [metrees* (mapcat (fn [metree]
                                                            (cond
                                                              (is-a? maybe-changed metree)
                                                              [(mark-deleted metree)]

                                                              (is-a? added metree)
                                                              []

                                                              (is-a? deleted metree)
                                                              [metree]))
                                                          metrees)]
                                     (assoc new-props pred* metrees*))
                                   ;; else
                                   (assoc new-props pred* metrees)))
                               {}
                               old-props)
                       ))
      (lens/overhaul (lens/>> edit-node-properties
                              (lens/member pred))
                     conj
                     (added added-result-value etree))))

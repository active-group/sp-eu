(ns wisen.frontend.edit-tree
  "Turn a rooted tree (wisen.frontend.tree) into an edit tree, tracking changes"
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [wisen.frontend.change :as change]))

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

(declare edit-tree-handle)

(defn same? [x]
  (and (is-a? maybe-changed x)
       (= (edit-tree-handle (maybe-changed-original-value x))
          (edit-tree-handle (maybe-changed-result-value x)))))

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

;; ---

(declare edit-tree)

(def-record edit-node
  [edit-node-uri :- tree/URI
   edit-node-properties :- (realm/map-of tree/URI ; predicate
                                         (realm/sequence-of
                                          ;; payload: edit-tree
                                          marked))])

(defn edit-node? [x]
  (is-a? edit-node x))

(def edit-tree (realm/union
                tree/ref
                tree/literal-string
                tree/literal-decimal
                tree/literal-boolean
                edit-node))

(declare edit-tree-original)

(defn- make-edit-tree [tree cns]
  (cond
    (tree/ref? tree)
    tree

    (tree/literal-string? tree)
    tree

    (tree/literal-decimal? tree)
    tree

    (tree/literal-boolean? tree)
    tree

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
    (tree/ref? etree)
    etree

    (tree/literal-string? etree)
    etree

    (tree/literal-decimal? etree)
    etree

    (tree/literal-boolean? etree)
    etree

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
                           (make-added (make-added-edit-tree object-tree))))))

(defn edit-tree-changes [etree]
  (cond
    (tree/ref? etree)
    []

    (tree/literal-string? etree)
    []

    (tree/literal-decimal? etree)
    []

    (tree/literal-boolean? etree)
    []

    (is-a? edit-node etree)
    (let [subject (edit-node-uri etree)]
      (mapcat
       (fn [[predicate metrees]]
         (mapcat (fn [metree]
                   (cond
                     (is-a? maybe-changed metree)
                     (concat
                      ;; changes on before tree
                      (edit-tree-changes
                       (maybe-changed-original-value metree))

                      ;; changes here
                      (if (changed? metree)
                        [(change/make-delete
                          (change/make-statement subject
                                                 predicate
                                                 (edit-tree-handle
                                                  (maybe-changed-original-value metree))))
                         (change/make-add
                          (change/make-statement subject
                                                 predicate
                                                 (edit-tree-handle
                                                  (maybe-changed-result-value metree))))]
                        [])

                      ;; changes on after tree
                      (edit-tree-changes
                       (maybe-changed-result-value metree)))

                     (is-a? added metree)
                     (conj
                      (edit-tree-changes
                       (added-result-value metree))
                      (change/make-add
                       (change/make-statement subject
                                              predicate
                                              (edit-tree-handle
                                               (added-result-value metree)))))

                     (is-a? deleted metree)
                     (conj
                      (edit-tree-changes
                       (deleted-original-value metree))
                      (change/make-delete
                       (change/make-statement subject
                                              predicate
                                              (edit-tree-handle
                                               (deleted-original-value metree)))))))

                 metrees))

       (edit-node-properties etree)))))

(defn edit-trees-changes [etrees]
  (mapcat edit-tree-changes etrees))

(defn edit-tree-commit-changes
  [etree]
  (cond
    (tree/ref? etree)
    etree

    (tree/literal-string? etree)
    etree

    (tree/literal-decimal? etree)
    etree

    (tree/literal-boolean? etree)
    etree

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

(defn edit-trees-commit-changes [etrees]
  (map edit-tree-commit-changes etrees))

(defn- edit-tree-handle [etree]
  (cond
    (tree/ref? etree)
    etree

    (tree/literal-string? etree)
    etree

    (tree/literal-decimal? etree)
    etree

    (tree/literal-boolean? etree)
    etree

    (is-a? edit-node etree)
    (edit-node-uri etree)))

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

     (tree/ref? etree)
     (tree/ref-uri etree)))

  ([etree uri]
   (cond
     (is-a? edit-node etree)
     (edit-node-uri etree uri)

     (tree/ref? etree)
     (tree/ref-uri etree uri))))

(defn node-type [enode]
  (tree/node-type (edit-tree-result-tree enode)))

(def primitive? tree/primitive?)

(def literal-string? tree/literal-string?)

(def literal-string-value tree/literal-string-value)

(def make-literal-string tree/make-literal-string)

(def literal-decimal? tree/literal-decimal?)

(def literal-decimal-value tree/literal-decimal-value)

(def literal-boolean? tree/literal-boolean?)

(def literal-boolean-value tree/literal-boolean-value)

(def make-ref tree/make-ref)

(def ref? tree/ref?)

(def ref-uri tree/ref-uri)

(defn graph->edit-trees [graph]
  (map make-same-edit-tree (tree/graph->trees graph)))

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

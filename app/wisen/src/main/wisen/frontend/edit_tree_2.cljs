(ns wisen.frontend.edit-tree-2
  "Turn a rooted tree (wisen.frontend.tree) into an edit tree, tracking changes"
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [wisen.frontend.change :as change]))

(def-record deleted
  [deleted-original-value
   deleted-result-value])

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
   (maybe-changed-original-value x)
   deleted-result-value
   (maybe-changed-result-value x)))

(def marked (realm/union
             deleted
             added
             maybe-changed))

;; ---

(declare edit-tree)

(def-record edit-node
  [edit-node-uri :- tree/URI
   edit-node-properties :- (realm/map-of tree/URI ; predicate
                                         (realm/sequence-of
                                          ;; payload: edit-tree
                                          marked))])

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

(defn- make-added-edit-tree [tree]
  (make-edit-tree tree make-added))

(defn- make-same-edit-tree [tree]
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
  (edit-node-properties enode (reduce (fn [eprops prop]
                                        (update eprops
                                                (tree/property-predicate prop)
                                                conj
                                                (make-same-edit-tree (tree/property-object prop))))
                                      {}
                                      (tree/node-properties new-node))))

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

;; re-implementations of wisen.frontend.tree stuff

#_(defn- lift-edit-tree [f]
  (comp f edit-tree-tree))

#_(defn make-node [uri]
  (make-edit-tree (tree/make-node uri)))

(def node? (partial is-a? edit-node))

(def node-uri edit-node-uri)

(defn node-type [enode]
  (tree/node-type (edit-tree-result-tree enode)))

(defn type-uri [type]
  (tree/type-uri type))

(def primitive? tree/primitive?)

(def literal-string? tree/literal-string?)

(def literal-string-value tree/literal-string-value)

(def literal-decimal? tree/literal-decimal?)

(def literal-decimal-value tree/literal-decimal-value)

(def literal-boolean? tree/literal-boolean?)

(def literal-boolean-value tree/literal-boolean-value)

(def make-ref tree/make-ref)

(def ref? tree/ref?)

(def ref-uri tree/ref-uri)

(defn graph->edit-trees [graph]
  (map make-same-edit-tree (tree/graph->trees graph)))

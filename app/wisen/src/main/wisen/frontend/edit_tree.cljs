(ns wisen.frontend.edit-tree
  "Turn a rooted tree (wisen.frontend.tree) into an edit tree, tracking changes"
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(def-record delete-property-edit
  [delete-property-edit-subject
   delete-property-edit-predicate
   delete-property-edit-object-tree])

(def-record add-property-edit
  [add-property-edit-subject
   add-property-edit-predicate
   add-property-edit-object-tree])

(def edit
  (realm/union
   delete-property-edit
   add-property-edit))

(def edit-subject
  (lens/conditional
   [(partial record/is-a? delete-property-edit) (partial record/is-a? delete-property-edit) delete-property-edit-subject]
   [(partial record/is-a? add-property-edit) (partial record/is-a? add-property-edit) add-property-edit-subject]
   (fn [])))

(def-record edit-tree
  [edit-tree-tree :- tree/tree
   edit-tree-edits :- (realm/set-of edit)])

(defn make-edit-tree
  ([tree]
   (make-edit-tree tree []))
  ([tree edits]
   (edit-tree edit-tree-tree tree
              edit-tree-edits edits)))

#_(def zip-edit-tree
  (lens/xmap
   (fn [[tree edits]]
     (edit-tree edit-tree-tree tree
                edit-tree-edits edits))
   (fn [etree]
     [(edit-tree-tree etree)
      (edit-tree-edits etree)])))

#_(def zip-edit-trees
  (lens/xmap

   (fn [[trees editss]]
     (println "zip yank")
     (map-indexed
      (fn [idx tree]
        (let [edits (nth editss idx [])]
          (make-edit-tree tree edits)))
      trees))

   (fn [edit-trees]
     (println "zip shove")
     (println (pr-str edit-trees))
     (reduce (fn [[trees edits] edit-tree]
               (println (pr-str edit-tree))
               [(conj trees (edit-tree-tree edit-tree))
                (concat edits (edit-tree-edits edit-tree))])
             [[] []]
             edit-trees
             ))))

(def-record edit-property
  [edit-property-predicate :- tree/URI
   edit-property-object :- (realm/delay edit-tree)])

(defn- edit-relevant? [tree edit]
  (if (record/is-a? tree/node tree)
    (or
     ;; either the edit mentions this very node ...
     (= (edit-subject edit)
        (tree/node-uri tree))
     ;; ... or any of the child nodes
     (some (fn [property]
             (edit-relevant? (tree/property-object property)
                             edit))
           (tree/node-properties tree)))
    ;; else
    false
    ))

(defn- trim-edits [tree edits]
  (filter (partial edit-relevant? tree) edits))

#_(defn- trim-edit-tree [etree]
  (let [tree (edit-tree-tree etree)
        edits (edit-tree-edits etree)
        trimmed-edits (trim-edits tree edits)]
    (edit-tree edit-tree-tree tree
               edit-tree-edits trimmed-edits)))

(defn edit-tree-property-at-index
  "edit-tree in, edit-property-out"
  [idx]

  (lens/lens

   (fn [etree]
     (let [orig-property (lens/yank etree (lens/>> edit-tree-tree tree/node-properties (lens/at-index idx)))
           orig-predicate (tree/property-predicate orig-property)
           orig-object (tree/property-object orig-property)]
       (edit-property edit-property-predicate orig-predicate
                      edit-property-object (edit-tree
                                            edit-tree-tree orig-object
                                            edit-tree-edits (trim-edits orig-object (edit-tree-edits etree))))))

   (fn [etree eprop]
     (let [prop-obj (edit-property-object eprop) ;; :- edit-tree
           prop-edits (edit-tree-edits prop-obj)
           prop-obj* (edit-tree-tree prop-obj) ;; :- tree
           old-tree (edit-tree-tree etree)     ;; :- tree
           new-tree (lens/shove old-tree
                                (lens/>> tree/node-properties (lens/at-index idx))
                                (tree/make-property (edit-property-predicate eprop)
                                                    prop-obj*))]
       (-> etree
           (edit-tree-tree new-tree)
           (lens/overhaul edit-tree-edits concat prop-edits))))))

;; re-implementations of wisen.frontend.tree stuff

(defn- lift-edit-tree [f]
  (comp f edit-tree-tree))

(defn make-node [uri]
  (make-edit-tree (tree/make-node uri)))

(def node? (lift-edit-tree tree/node?))

(def node-uri (lift-edit-tree tree/node-uri))

(defn node-properties "Get a list of edit-property" [etree]
  (map
   (fn [idx]
     (lens/yank etree (edit-tree-property-at-index idx)))
   (range
    (count (tree/node-properties
            (edit-tree-tree etree))))))

(def node-type
  (lens/>>
   edit-tree-tree
   tree/node-type))

(defn type-uri [type]
  (tree/type-uri type))

(def primitive? (lift-edit-tree tree/primitive?))

(def literal-string? (lift-edit-tree tree/literal-string?))

(def literal-string-value (lift-edit-tree tree/literal-string-value))

(def literal-decimal? (lift-edit-tree tree/literal-decimal?))

(def literal-decimal-value (lift-edit-tree tree/literal-decimal-value))

(def literal-boolean? (lift-edit-tree tree/literal-boolean?))

(def literal-boolean-value (lift-edit-tree tree/literal-boolean-value))

(def ref? (lift-edit-tree tree/ref?))

(def ref-uri (lift-edit-tree tree/ref-uri))

(defn graph->edit-trees [graph]
  (map make-edit-tree (tree/graph->trees graph)))

(defn edit-trees->graph [etrees]
  (tree/trees->graph (map edit-tree-tree etrees)))

(def graph<->edit-trees
  (lens/xmap graph->edit-trees
             edit-trees->graph))

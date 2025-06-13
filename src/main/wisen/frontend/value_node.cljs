(ns wisen.frontend.value-node
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [wisen.common.prefix :as prefix]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]))

;; A value node is a normal tree/node, but we want to treat it as a
;; pure value, not an entity with an id (uri) This means we have to
;; derive the node's URI from the contents of its properties.

(defn properties-derive-uri [props]
  (prefix/resource (hash (set props))))

(defn edit-properties-derive-uri [eprops]
  (let [dummy-uri "http://example.org/root"
        enode* (edit-tree/edit-node edit-tree/edit-node-uri dummy-uri
                                    edit-tree/edit-node-properties eprops)]
    (-> enode*
        (edit-tree/edit-node-result-node)
        (tree/node-properties)
        (properties-derive-uri))))


(c/defn-item as-value-node [item]
  (c/handle-state-change
   (c/focus edit-tree/edit-node-properties item)
   (fn [old-node new-node]
     (let [old-eprops (edit-tree/edit-node-properties old-node)
           new-eprops (edit-tree/edit-node-properties new-node)]
       (if (= old-eprops new-eprops)
         old-node
         (edit-tree/edit-node-uri new-node
                                  (edit-properties-derive-uri new-eprops)))))))

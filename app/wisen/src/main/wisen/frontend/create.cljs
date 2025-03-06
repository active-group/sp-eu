(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.util :as util]))

;; Confluence
;; x -> y -> z
;; x -> a -> z

;; Cycle
;; x -> y
;; y -> x

(def organization-type (tree/make-node "http://schema.org/Organization"))
(def event-type (tree/make-node "http://schema.org/Event"))

(defn main []
  (ds/padded-2
   {:style {:overflow "auto"}}
   (dom/h2 "Create a new resource")
   (c/isolate-state
    (-> (tree/make-node)
        (tree/node-type organization-type))
    (util/with-schemaorg
      (fn [schema]
        (editor/tree-component schema [organization-type event-type] true true false false))))))

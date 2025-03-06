(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.util :as util]
            [wisen.frontend.change :as change]
            [reacl-c-basics.ajax :as ajax]))

(def organization-type (tree/make-node "http://schema.org/Organization"))
(def event-type (tree/make-node "http://schema.org/Event"))

(def ^:private empty-tree
  (-> (tree/make-node)
      (tree/node-type organization-type)))

(defn main []
  (ds/padded-2
   {:style {:overflow "auto"}}
   (dom/h2 "Create a new resource")
   (c/isolate-state
    empty-tree
    (dom/div
     (util/with-schemaorg
       (fn [schema]
         (editor/tree-component schema [organization-type event-type] true true false false)))

     ;; commit
     (c/with-state-as [tree local-state :local {:commit? false
                                                :last-saved-tree empty-tree
                                                :last-error nil}]
       (let [changes (change/delta-tree (:last-saved-tree local-state) tree)]
         (c/focus lens/second
                  (dom/div

                   (when (:commit? local-state)
                     (c/handle-action
                      (ajax/execute (change/commit-changes-request changes))
                      (fn [st ac]
                        (if (and (ajax/response? ac)
                                 (ajax/response-ok? ac))
                          (-> st
                              (assoc :commit? false)
                              (assoc :last-saved-tree tree)
                              (dissoc :last-error))
                          (-> st
                              (assoc :commit? false)
                              (assoc :last-error (ajax/response-value ac)))))))

                   (if (empty? changes)
                     "saved"
                     (str (count changes) " changes"))

                   (c/focus :commit?
                            (dom/button {:onclick (constantly true)}
                                        "Commit changes"))))))))))

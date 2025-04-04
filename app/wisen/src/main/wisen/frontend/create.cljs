(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree-2 :as edit-tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.util :as util]
            [wisen.frontend.change :as change]
            [wisen.frontend.default :as default]
            [reacl-c-basics.ajax :as ajax]))

(def organization-type (tree/make-node "http://schema.org/Organization"))
(def event-type (tree/make-node "http://schema.org/Event"))

(defonce ^:private empty-tree
  (tree/make-node))

(defonce ^:private initial-organization
  default/default-organization)

(defn main []
  (util/with-schemaorg
    (fn [schema]
      (ds/padded-2
       {:style {:overflow "auto"}}
       (dom/h2 "Create a new resource")
       (c/isolate-state
        (edit-tree/make-added-edit-tree initial-organization)
        (c/with-state-as etree
          (dom/div
           (pr-str (edit-tree/edit-tree-changes etree))
           (dom/div
            #_{:style {:background "rgba(170,170,170,1.0)"
                       :border "1px solid gray"
                       :border-radius "4px"
                       :padding "8px 16px"}}
            (editor/edit-tree-component schema [organization-type event-type] true true))

           ;; commit
           #_(c/with-state-as [tree local-state :local {:commit? false
                                                        :last-saved-tree empty-tree
                                                        :last-error nil}]
               (let [changes (change/delta-tree (:last-saved-tree local-state) tree)]
                 (c/focus lens/second
                          (dom/div

                           (change/changes-component schema changes)

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
                                                "Commit changes")))))))))))))

(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
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

(def-record idle [])

(def-record committing [committing-changes])

(def-record commit-successful [commit-successful-changes])

(def-record commit-failed [commit-failed-error])

(defn changes-component [schema changes]
  (c/isolate-state
   (idle)
   (c/with-state-as state
     (dom/div

      (change/changes-summary schema changes)

      (cond
        (is-a? idle state)
        (when-not (empty? changes)
          (dom/button {:onclick (fn [_]
                                  (committing committing-changes changes))}
                      "Commit changes"))

        (is-a? committing state)
        (dom/div
         "Committing ..."
         (wisen.frontend.spinner/main)
         (c/handle-action
          (ajax/execute (change/commit-changes-request (committing-changes state)))
          (fn [st ac]
            (if (and (ajax/response? ac)
                     (ajax/response-ok? ac))
              (c/return :state (commit-successful commit-successful-changes
                                                  (committing-changes st))
                        :action (commit-successful commit-successful-changes
                                                   (committing-changes st)))
              (commit-failed commit-failed-error
                             (ajax/response-value ac))))))

        (is-a? commit-successful state)
        "Success!"

        (is-a? commit-failed state)
        (dom/div
         "Commit failed!"
         (dom/pre
          (pr-str (commit-failed-error state)))))))))

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

           (c/handle-action
            (changes-component schema (edit-tree/edit-tree-changes etree))
            (fn [etree action]
              (if (is-a? commit-successful action)
                (c/return :state (edit-tree/edit-tree-commit-changes etree))
                (c/return))))

           (editor/edit-tree-component schema [organization-type event-type] true true))))))))

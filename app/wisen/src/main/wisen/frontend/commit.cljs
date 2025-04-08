(ns wisen.frontend.commit
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.change :as change]
            [reacl-c-basics.ajax :as ajax]))

(def-record idle [])

(def-record committing [committing-changes])

(def-record commit-successful [commit-successful-changes])

(def-record commit-failed [commit-failed-error])

(defn changes-component [schema changes]
  (c/isolate-state
   (idle)
   (c/with-state-as state
     (dom/div
      {:style {:display "flex"
               :gap "1em"}}

      (change/changes-summary schema changes)

      (cond
        (is-a? idle state)
        (when-not (empty? changes)
          (ds/button-primary {:onclick (fn [_]
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

(defn main [schema]
  (c/with-state-as etrees
    (dom/div
     {:style {:border-top ds/border
              :padding "12px 24px"
              :display "flex"
              :justify-content "flex-end"}}
     (c/handle-action
      (changes-component schema (edit-tree/edit-trees-changes etrees))
      (fn [etree action]
        (if (is-a? commit-successful action)
          (c/return :state (edit-tree/edit-trees-commit-changes etrees))
          (c/return)))))))

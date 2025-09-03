(ns wisen.frontend.commit
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.change :as change]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.context :as context]
            [wisen.frontend.translations :as tr]))

(def-record idle [])

(def-record committing [committing-changeset])

(def-record commit-successful [commit-successful-changes])

(def-record commit-failed [commit-failed-error])

(defn changeset-component [ctx changeset]
  (c/isolate-state
   (idle)
   (c/with-state-as state
     (dom/div
      {:style {:display "flex"
               :gap "1em"}}

      (change/changeset-summary ctx changeset)

      (cond
        (is-a? idle state)
        (when-not (empty? changeset)
          (ds/button-primary {:onclick (fn [_]
                                         (committing committing-changeset changeset))}
                             (context/text ctx tr/commit-changes)))

        (is-a? committing state)
        (dom/div
         (context/text ctx tr/committing)
         (wisen.frontend.spinner/main)
         (c/handle-action
          (ajax/execute (change/commit-changeset-request (committing-changeset state)))
          (fn [st ac]
            (if (and (ajax/response? ac)
                     (ajax/response-ok? ac))
              (c/return :state (commit-successful commit-successful-changes
                                                  (committing-changeset st))
                        :action (commit-successful commit-successful-changes
                                                   (committing-changeset st)))
              (commit-failed commit-failed-error
                             (ajax/response-value ac))))))

        (is-a? commit-successful state)
        (context/text ctx tr/commit-successful)

        (is-a? commit-failed state)
        (dom/div
         (context/text ctx tr/commit-failed)
         (dom/pre
          (pr-str (commit-failed-error state)))))))))

(defn main [ctx & additional-items]
  (c/with-state-as etree
    (dom/div
     {:style {:padding "12px 24px"
              :display "flex"}}
     (c/handle-action
      (dom/div
       {:style {:display "flex"
                :flex 1
                :justify-content "space-between"}}
       (apply dom/div additional-items)
       (changeset-component ctx (edit-tree/edit-tree-changeset etree)))
      (fn [etree action]
        (if (is-a? commit-successful action)
          (c/return :state (edit-tree/commit etree))
          (c/return :action action)))))))

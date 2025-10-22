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
            [wisen.frontend.head :as head]
            [wisen.frontend.translations :as tr]
            [wisen.common.urn :as urn]
            [wisen.common.prefix :as prefix]
            [cljs.reader :as reader]))

(def-record idle [])

(def-record committing [committing-changeset
                        committing-commit-message])

(def-record commit-successful [commit-successful-changes
                               commit-successful-result-commit-id
                               commit-successful-result-basis])

(def-record commit-failed [commit-failed-error])

(defn changeset-component [ctx changeset]
  (c/isolate-state
   (idle)
   (c/with-state-as state
     (dom/div
      {:style {:display "flex"
               :align-items "center"
               :gap "1em"}}

      (change/changeset-summary ctx changeset)

      (cond
        (is-a? idle state)
        (when-not (empty? changeset)
          (c/local-state
           "Update"
           (dom/div
            {:style {:display "flex"
                     :gap "1em"}}
            (c/focus lens/second
                     (c/with-state-as msg
                       (ds/input {:type "text"
                                  :style {:padding "1ex 1em"
                                          :border "1px solid #ddd"
                                          :background "#ececec"
                                          :field-sizing "content"}})))
            (ds/button-primary {:onclick (fn [[_ commit-message]]
                                           [(committing committing-changeset changeset
                                                        committing-commit-message commit-message)
                                            ""])}
                               (context/text ctx tr/commit-changes)))))

        (is-a? committing state)
        (dom/div
         (context/text ctx tr/committing)
         (wisen.frontend.spinner/main)
         (c/handle-action
          (ajax/execute (change/commit-changeset-request (context/commit-id ctx)
                                                         (committing-changeset state)
                                                         (committing-commit-message state)))
          (fn [st ac]
            (if (and (ajax/response? ac)
                     (ajax/response-ok? ac))
              (let [response-edn (reader/read-string (ajax/response-value ac))
                    success (commit-successful commit-successful-changes
                                               (committing-changeset st)
                                               commit-successful-result-commit-id
                                               (:result-commit-id response-edn)
                                               commit-successful-result-basis
                                               (:result-basis response-edn))]
                (c/return :state success
                          :action success))
              (commit-failed commit-failed-error
                             (ajax/response-value ac))))))

        (is-a? commit-successful state)
        (dom/div
         {:style {:display "flex"
                  :gap "1em"}}
         (context/text ctx tr/commit-successful)
         (let [results (commit-successful-result-basis state)
               result (first results)]
           (dom/a {:href (if (urn/urn? result)
                           (prefix/resource-description result)
                           result)}
                  (context/text ctx tr/go-to-result))))

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
          (c/return #_#_:state (edit-tree/commit etree)
                    :action (head/make-set-head-commit-id-action
                             (commit-successful-result-commit-id action)))
          (c/return :action action)))))))

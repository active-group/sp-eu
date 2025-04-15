(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.util :as util]
            [wisen.frontend.change :as change]
            [wisen.frontend.default :as default]
            [wisen.frontend.commit :as commit]
            [reacl-c-basics.ajax :as ajax]))

(def organization-type (tree/make-node "http://schema.org/Organization"))
(def event-type (tree/make-node "http://schema.org/Event"))

(defonce ^:private initial-organization
  default/default-organization)

(defn main

  ([schema]
   (main schema initial-organization))

  ([schema initial-tree & additional-items]
   (c/isolate-state

    [(edit-tree/make-added-edit-tree initial-tree)]

    (c/with-state-as etrees
      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :overflow "auto"}}

       (dom/div
        {:style {:overflow "auto"
                 :padding "3ex 2em"}}

        (editor/edit-trees-component schema [organization-type event-type] true true))

       (apply commit/main
              schema
              additional-items))))))

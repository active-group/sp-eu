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

    (edit-tree/make-added-edit-tree initial-tree)

    (c/with-state-as etree
      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :overflow "auto"}}

       (dom/div
        {:style {:overflow "auto"
                 :padding "3ex 2em"
                 :scroll-behavior "smooth"}}

        (editor/edit-tree-component schema [organization-type event-type] true true))

       (apply commit/main
              schema
              additional-items))))))

(c/defn-item from-rdf [attrs schema initial-string]
  (c/isolate-state
   {:text [initial-string (wisen.frontend.forms/selected)]
    :graphs nil}

   (dom/div
    (dom/merge-attributes
     {:style {:display "flex"
              :overflow "auto"}}
     attrs)

    (c/focus :text
             (dom/div
              {:style {:border-right "1px solid #777"
                       :flex 1
                       :min-height "64ex"
                       :overflow "auto"}}
              (ds/textarea+focus {:style {:width "100%"
                                          :height "100%"}})))

    (dom/div
     {:style {:flex 1
              :overflow "auto"}}

     (c/with-state-as st

       (util/json-ld-string->graph (first (:text st))
                                   (fn [graph]
                                     (ds/padded-2
                                      (editor/readonly-graph schema graph "white"))
                                     )))))))

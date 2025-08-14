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

(def organization-type "http://schema.org/Organization")
(def event-type "http://schema.org/Event")

(defonce ^:private initial-organization
  default/default-organization)

(defn main

  ([ctx]
   (main ctx initial-organization))

  ([ctx initial-tree & additional-items]
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

        (editor/edit-tree-component ctx [organization-type event-type] true true))

       (dom/div
        {:style {:border-top ds/border}}
        (apply commit/main
               ctx
               additional-items)))))))

(c/defn-item from-rdf [attrs ctx initial-string & additional-items]
  (c/isolate-state
   {:text [initial-string (wisen.frontend.forms/selected)]
    :edit-trees {} ;; text -> edit-tree
    }

   (c/with-state-as state
     (let [text (first (:text state))]

       (dom/div
        (dom/merge-attributes
         {:style {:display "flex"
                  :flex-direction "column"
                  :overflow "auto"}}
         attrs)

        (dom/div
         (dom/merge-attributes
          {:style {:display "flex"
                   :overflow "auto"}}
          attrs)

         ;; left pane: text input
         (c/focus :text
                  (dom/div
                   {:style {:border-right "1px solid #777"
                            :flex 1
                            :min-height "64ex"
                            :overflow "auto"}}
                   (ds/textarea+focus {:style {:width "100%"
                                               :height "100%"}})))

         ;; run text -> edit-tree
         (c/focus (lens/>> :edit-trees
                           (lens/member text))
                  (c/with-state-as etree
                    (when-not etree
                      (util/json-ld-string->graph text
                                                  (fn [graph]
                                                    (c/once
                                                     (fn [_]
                                                       (c/return :state
                                                                 (edit-tree/graph->addit-tree graph)))))))))

         (dom/div
          {:style {:flex "1"
                   :overflow "auto"}}

          (c/focus (lens/>> :edit-trees
                            (lens/member text))
                   (ds/padded-2
                    (editor/edit-tree-component ctx nil false false)))))

        ;; bottom commit bar
        (c/focus (lens/>> :edit-trees
                          (lens/member text))
                 (c/with-state-as etree
                   (dom/div
                    {:style {:border-top ds/border}}
                    (apply commit/main
                           ctx
                           additional-items)))))))))

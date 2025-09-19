(ns wisen.frontend.resource
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [active.clojure.lens :as lens]
            [wisen.frontend.util :as util]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.spinner :as spinner]
            [wisen.frontend.commit :as commit]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.context :as context]
            [wisen.common.or-error :refer [success?
                                           success-value]]))

(defn- resource-request [commit-id id]
  (ajax/GET (str "/resource/" id "/about")
            {:headers {"accept" "application/ld+json"}
             :params {"base-commit-id" commit-id}}))

(c/defn-item main [ctx resource-id]
  (ds/padded-2
   {:style {:overflow-y "auto"}}
   (c/isolate-state
    {}
    (c/focus (lens/member (context/commit-id ctx))
             (c/with-state-as result
               (if (nil? result)
                 (c/fragment
                  (spinner/main)
                  (-> (util/load-json-ld
                       (resource-request (context/commit-id ctx) resource-id))
                      (c/handle-action (fn [st ac]
                                         (if (success? ac)
                                           (edit-tree/graph->edit-tree
                                            (success-value ac))
                                           (assert false "TODO: implement error handling"))))))
                 ;; else
                 (dom/div
                  (editor/edit-tree-component ctx nil true false nil)
                  (let [show-commit-bar? (not-empty (edit-tree/edit-tree-changeset result))]
                    (dom/div
                     {:style {:border ds/border
                              :position "sticky"
                              :bottom (if show-commit-bar?
                                        "10px"
                                        "-60px")
                              :transition "bottom 0.3s"
                              :border-radius "4px"
                              :background "#ddd"
                              :z-index "999"}}
                     (commit/main ctx))))))))))

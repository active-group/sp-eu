(ns wisen.frontend.resource
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [wisen.frontend.util :as util]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.spinner :as spinner]
            [wisen.frontend.commit :as commit]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.common.or-error :refer [success?
                                           success-value]]))

(defn- resource-request [id]
  (ajax/GET (str "/resource/" id "/about")
            {:headers {"accept" "application/ld+json"}}))

(c/defn-item main [ctx resource-id]
  (ds/padded-2
   {:style {:overflow-y "auto"}}
   (c/isolate-state
    nil
    (c/with-state-as result
      (if (nil? result)
        (c/fragment
         (spinner/main)
         (-> (util/load-json-ld
              (resource-request resource-id))
             (c/handle-action (fn [st ac]
                                (if (success? ac)
                                  (edit-tree/graph->edit-tree
                                   (success-value ac))
                                  (assert false "TODO: implement error handling"))))))
        ;; else
        (dom/div
         (editor/edit-tree-component ctx nil true false nil)
         (when-not (empty? (edit-tree/edit-tree-changeset result))
           (dom/div
            {:style {:border ds/border
                     :position "sticky"
                     :bottom "10px"
                     :border-radius "4px"
                     :background "#ddd"
                     :z-index "999"}}
            (commit/main ctx)))))))))

(ns wisen.frontend.resource
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [wisen.frontend.util :as util]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.design-system :as ds]
            [wisen.common.or-error :refer [success?
                                           success-value]]))

(defn- resource-request [id]
  (ajax/GET (str "/resource/" id "/about")
            {:headers {"accept" "application/ld+json"}}))

(c/defn-item main [schema resource-id]
  (ds/padded-2
   (c/isolate-state
    nil
    (c/with-state-as result
      (if (nil? result)
        (c/fragment
         "loading"
         (-> (util/load-json-ld
              (resource-request resource-id))
             (c/handle-action (fn [st ac]
                                (if (success? ac)
                                  (success-value ac)
                                  (assert false "TODO: implement error handling"))))))
        ;; else
        (editor/edit-graph schema true false result)
        )))))

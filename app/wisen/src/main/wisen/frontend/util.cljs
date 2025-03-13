(ns wisen.frontend.util
  (:require [reacl-c.core :as c :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.spinner :as spinner]
            [wisen.frontend.or-error :refer [make-success
                                             success?
                                             success-value
                                             make-error]]))

(c/defn-item load-json-ld
  "Loads some JSON-LD for the given request. Parses the JSON-LD and
  returns either a success action with the graph (rdf) or an error
  action"
  [request]
  (c/with-state-as [graph response :local nil]
    (c/fragment
     (c/focus lens/second
              (ajax/fetch request))

     (when (ajax/response? response)
       (if (ajax/response-ok? response)
         (promise/call-with-promise-result
          (rdf/json-ld-string->graph-promise (ajax/response-value response))
          (fn [response-graph]
            (c/once
             (fn [[st resp]]
               (c/return :action (make-success response-graph))))))
         (c/once
          (fn [_]
            (c/return :action (make-error (ajax/response-value response))))))))))

(c/defn-item load-json-ld-state [request]
  (-> (load-json-ld request)
      (c/handle-action (fn [_ ac]
                         (if (success? ac)
                           (c/return :state (success-value ac))
                           (c/return :action ac))))))

(defn load-schemaorg []
  (load-json-ld-state (ajax/GET "/api/schema"
                                {:headers {:accept "application/ld+json"}})))

(c/defn-item with-schemaorg [k]
  (c/with-state-as [state graph :local nil]
    (c/fragment
     (if (nil? graph)
       (c/fragment
        (spinner/main {:style {:padding "32px"}}
                      "Loading schema")
        (c/focus lens/second (load-schemaorg)))
       (c/focus lens/first (k graph))))))

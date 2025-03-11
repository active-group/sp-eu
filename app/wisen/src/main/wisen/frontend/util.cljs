(ns wisen.frontend.util
  (:require [reacl-c.core :as c :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.spinner :as spinner]))

(c/defn-item load-schemaorg []
  (c/with-state-as [graph response :local nil]
    (c/fragment
     (c/focus lens/second
              (ajax/fetch (ajax/GET "/api/schema"
                                    {:headers {:accept "application/ld+json"}})))

     (when (ajax/response? response)
       (if (ajax/response-ok? response)
         (promise/call-with-promise-result
          (rdf/json-ld-string->graph-promise (ajax/response-value response))
          (fn [response-graph]
            (c/once
             (fn [_]
               (c/return :state [response-graph response])))))
         (c/once
          (fn [_]
            (c/return :action ::TODO-schemaorg-error))))))))

(c/defn-item with-schemaorg [k]
  (c/with-state-as [state graph :local nil]
    (c/fragment
     (if (nil? graph)
       (c/fragment
        spinner/main
        (c/focus lens/second (load-schemaorg)))
       (c/focus lens/first (k graph))))))

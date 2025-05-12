(ns wisen.frontend.util
  (:require [reacl-c.core :as c :include-macros true]
            [active.clojure.lens :as lens]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.spinner :as spinner]
            [wisen.common.or-error :refer [make-success
                                           success?
                                           success-value
                                           make-error]]))

(c/defn-item load-json-ld
  "Loads some JSON-LD for the given request. Parses the JSON-LD and
  returns either a success action with the graph (rdf) or an error
  action"
  [request]
  (c/isolate-state
   {}
   (c/with-state-as responses
     (c/fragment
      (c/focus (lens/member request)
               (ajax/fetch request))

      (when-let [current-response (get responses request)]
        (if (ajax/response-ok? current-response)
          (promise/call-with-promise-result
           (rdf/json-ld-string->graph-promise (ajax/response-value current-response))
           (fn [response-graph]
             (c/once
              (fn [_]
                (c/return :action (make-success response-graph))))))
          ;; else
          (c/once
           (fn [_]
             (c/return :action (make-error (ajax/response-value current-response)))))))))))

(c/defn-item load-json-ld-state [request]
  (c/with-state-as graph
    (when (nil? graph)
      (-> (load-json-ld request)
          (c/handle-action (fn [_ ac]
                             (if (success? ac)
                               (c/return :state (success-value ac))
                               (c/return :action ac))))))))
(c/defn-item load-json-ld-state* [request]
  (c/with-state-as graph
    (when (nil? graph)
      (-> (load-json-ld request)
          (c/handle-action (fn [_ ac]
                             (c/return :state ac)))))))


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

(defn inspect-lens [label]
  (fn
    ([x]
     (println (str label " – YANK"))
     (println (pr-str x))
     x)
    ([x y]
     (println (str label " – SHOVE"))
     (println (pr-str x))
     (println (pr-str y))
     y
     )))

(defn cond-lens [[rlm1 lns1] [rlm2 lns2] & cases]
  (let [latter-lens
        (if (empty? cases)
          lns2
          (apply cond-lens [rlm2 lns2] cases))]

    (lens/either
     (fn [x] (is-a? rlm1 x))
     (fn [x _] (is-a? rlm1 x))
     lns1
     latter-lens)))

(deftype ^:private F [f m]
  IFn
  (-invoke [this arg]
    (if-let [res (get m arg)]
      res
      (f arg))))

(defn fn-at [arg]
  (lens/lens
   (fn [f]
     (f arg))
   (fn [f res]
     (if (= (f arg) res)
       f
       (let [[f* m*] (if (instance? F f)
                       [(.-f f) (.-m f)]
                       [f {}])]
         (F. f* (assoc m* arg res)))))))

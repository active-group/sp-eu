(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]))

;; [ ] Fix links for confluences
;; [ ] Load all properties
;; [ ] Focus
;; [ ] Patterns for special GUIs
;; [ ] Style

(defn- triple-pattern* [current-level target-level]
  (if (> current-level target-level)
    ""
    (str (str "?x" current-level " ?p" current-level " ?x" (inc current-level) " .")
         "\n"
         (triple-pattern* (inc current-level) target-level))))

(defn- triple-pattern [level]
  (triple-pattern* 1 level))

(defn- load-more-query [uri level]
  (str "CONSTRUCT { <" uri "> ?p ?x1 .
                   " (triple-pattern level) " }
          WHERE { <" uri "> ?p ?x1 .
                  " (triple-pattern level) " }"))

(load-more-query "foobar" 2)

(defn- load-more-request [uri level]
  (ajax/POST "/api/search"
                 {:body (js/JSON.stringify (clj->js {:query (load-more-query uri level)}))
                  :headers {:content-type "application/json"}
                  #_#_:response-format "application/ld+json"}))

(defn- load-more [uri level]
  (c/with-state-as [state response :local nil]
    (c/fragment
     (c/focus lens/second
              (ajax/fetch (load-more-request uri level)))

     (when (ajax/response? response)
       (if (ajax/response-ok? response)
         (promise/call-with-promise-result
          (rdf/json-ld-string->graph-promise (ajax/response-value response))
          (fn [response-graph]
            (c/once
             (fn [_]
               (c/return :action (ajax/ok-response response-graph))))))
         (c/once
          (fn [_]
            (c/return :action response))))))))

(defn- load-more-button [uri]
  (c/with-state-as [graph local-state :local {:go false
                                              :level 0
                                              :error nil}]
    (c/fragment
     (c/focus (lens/>> lens/second :go)
              (dom/div
               (pr-str local-state)
               (dom/button {:onclick (constantly true)} "Load all properties")))

     (when (:go local-state)
       (c/handle-action
        (load-more uri (:level local-state))
        (fn [[graph local-state] response]
          (if (ajax/response-ok? response)
            (c/return :state [(rdf/merge graph (ajax/response-value response))
                              (-> local-state
                                  (assoc :go false)
                                  (update :level inc))])
            (c/return :state [graph
                              (assoc local-state :error (ajax/response-value response))]))))))))

(defn predicate-component [pred]
  (rdf/symbol-uri pred))

(declare node-component)

(defn property [graph links property]
  (let [[links* it] (node-component graph links (rdf/property-object property))]
    [links*
     (dom/div
      (dom/div (predicate-component (rdf/property-predicate property)))
      (dom/div {:style {:margin-left "2em"}} it))]))

(defn resource-component [graph links x]
  (let [uri (rdf/node-uri x)
        link-here uri
        links* (assoc links uri link-here)
        [links** lis] (reduce (fn [[links its] prop]
                                (let [[links* it] (property graph links prop)]
                                  [links* (conj its it)]))
                              [links* []]
                              (rdf/subject-properties graph x))]
    [links**
     (dom/div
      {:id link-here
       :style {:border "1px solid gray"
               :padding 12}}
      uri

      (when (rdf/symbol? x)
        (load-more-button uri))

      (apply
       dom/ul
       lis))]))

(defn node-component [graph links x]
  (cond
    (or (rdf/symbol? x)
        (rdf/blank-node? x))
    (if-let [link (get links (rdf/node-uri x))]
      [links (dom/a {:href (str "#" link)} "Go #")]
      (resource-component graph links x))

    (rdf/literal-string? x)
    [links (rdf/literal-string-value x)]

    (rdf/collection? x)
    [links (pr-str x)]))

(defn main []
  (c/with-state-as graph
    (dom/div
     (pr-str graph)
     (dom/hr)
     (apply
      dom/div
      (second (reduce (fn [[links its] x]
                        (let [[links* it] (node-component graph links x)]
                          [links* (conj its it)]))
                      [{} []]
                      (rdf/roots graph)))))))

(defn readonly [graph]
  (c/isolate-state graph (main)))

(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]))

;; [ ] Fix links for confluences
;; [x] Load all properties
;; [x] Focus
;; [ ] Patterns for special GUIs
;; [x] Style

(defn- special-property? [property]
  (let [predicate (tree/property-predicate property)]
    (some #{predicate} ["http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                        "http://schema.org/name"
                        "http://schema.org/description"])))

(defn pr-type [t]
  (case t
    "http://schema.org/GeoCoordinates"
    "Geo coordinates"

    "http://schema.org/GeoCircle"
    "Geo circle"

    "http://schema.org/Organization"
    "Organization"

    t
    ))

(defn pr-predicate [p]
  (case p
    "http://schema.org/name"
    "Name"

    "http://schema.org/email"
    "E-Mail"

    "http://schema.org/url"
    "Website"

    "http://schema.org/geo"
    "The geo coordinates of the place"

    "http://schema.org/description"
    "Description"

    "http://schema.org/keywords"
    "Keywords"

    "http://schema.org/areaServed"
    "Area served"

    "http://schema.org/latitude"
    "Latitude"

    "http://schema.org/longitude"
    "Longitude"

    p))

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
              (dom/button {:style {}
                           :onclick (constantly true)} (if (> (:level local-state) 0)
                                                         "v"
                                                         ">")))

     (when-let [error (:error local-state)]
       (dom/div
        "Oh no, an error occurred"
        (dom/div (pr-str error))))

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
  (dom/strong
   (pr-predicate pred)))

(declare tree-component)

(defn property-component [prop]
  (dom/div
   (dom/div (predicate-component (tree/property-predicate prop)))
   (dom/div {:style {:margin-left "0em"
                     :margin-top "1ex"
                     :display "flex"}}
            (tree-component (tree/property-object prop)))))

(defn- focus-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?o . }
          WHERE { <" uri "> ?p ?o . }"))

(defn- node-type [node]
  (some (fn [prop]
          (when (= (tree/property-predicate prop)
                   "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
            (let [obj (tree/property-object prop)]
              (when (tree/node? obj)
                (tree/node-uri obj)))))
        (tree/node-properties node)))

(defn- node-name [node]
  (some (fn [prop]
          (when (= (tree/property-predicate prop)
                   "http://schema.org/name")
            (let [obj (tree/property-object prop)]
              (when (tree/literal-string? obj)
                (tree/literal-string-value obj)))))
        (tree/node-properties node)))

(defn- node-description [node]
  (some (fn [prop]
          (when (= (tree/property-predicate prop)
                   "http://schema.org/description")
            (let [obj (tree/property-object prop)]
              (when (tree/literal-string? obj)
                (tree/literal-string-value obj)))))
        (tree/node-properties node)))

(defn- node-component [node]
  (let [uri (tree/node-uri node)
        lis (mapcat (fn [prop]
                      (if (special-property? prop)
                        []
                        [(dom/li
                          (property-component prop))]))
                    (tree/node-properties node))]
    (ds/card
     {:id uri}

     ;; header
     (dom/div {:style {:display "flex"
                       :justify-content "flex-start"
                       :align-items "center"
                       :background "rgba(0,0,0,0.1)"}}

              (ds/padded-1
               (load-more-button uri))

              (dom/div
               (ds/padded-1
                {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
                (if-let [type (node-type node)]
                  (pr-type type)
                  "Resource"))


               (ds/padded-1
                {:style {:color "#555"
                         :font-size "12px"}}
                uri))

              (ds/padded-1
               (dom/button {:onClick
                            (fn [_]
                              (c/return :action
                                        (focus-query uri)))}
                           "Focus")))

     (when-let [name (node-name node)]
       (ds/with-card-padding
         (dom/div {:style {:font-size "2em"}}
                  name)))

     (when-let [description (node-description node)]
         (ds/with-card-padding description))

     (when-not (empty? lis)
       (ds/with-card-padding
         (apply
          dom/ul
          {:style {:display "flex"
                   :flex-direction "column"
                   :gap "2ex"}}
          lis))))))

(defn tree-component [tree]
  (cond
    (tree/node? tree)
    (node-component tree)

    (tree/literal-string? tree)
    (tree/literal-string-value tree)

    (tree/ref? tree)
    (dom/div "REF: " (tree/ref-uri tree))))

(c/defn-item main* []
  (c/with-state-as trees
    (apply
     dom/div
     (map tree-component trees))))

(defn main []
  (c/focus tree/graph<->trees (main*)))

(defn readonly [graph]
  (c/isolate-state graph (main)))

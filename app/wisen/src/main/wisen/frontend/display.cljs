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
;; [x] Load all properties
;; [ ] Focus
;; [ ] Patterns for special GUIs
;; [x] Style

(defn- special-property? [property]
  (let [predicate (rdf/property-predicate property)
        uri (rdf/symbol-uri predicate)]
    (some #{uri} ["http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                  "http://schema.org/name"
                  "http://schema.org/description"])))

(defn pr-type [t]
  (let [uri (rdf/symbol-uri t)]
    (case uri
      "http://schema.org/GeoCoordinates"
      "Geo coordinates"

      "http://schema.org/GeoCircle"
      "Geo circle"

      "http://schema.org/Organization"
      "Organization"

      uri
      )))

(defn pr-predicate [p]
  (let [uri (rdf/symbol-uri p)]
    (case uri
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

      uri)))

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
                           :onclick (constantly true)} "Load more"))

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

(declare node-component)

(defn property [graph links property]
  (let [[links* it] (node-component graph links (rdf/property-object property))]
    [links*
     (dom/div
      (dom/div (predicate-component (rdf/property-predicate property)))
      (dom/div {:style {:margin-left "0em"
                        :margin-top "1ex"
                        :display "flex"}} it))]))

(defn resource-component [graph links x]
  (let [uri (rdf/node-uri x)
        link-here uri
        links* (assoc links uri link-here)
        [links** lis] (reduce (fn [[links its] prop]
                                (if (special-property? prop)
                                  [links its]
                                  (let [[links* it] (property graph links prop)]
                                    [links* (conj its (dom/li it))])))
                              [links* []]
                              (rdf/subject-properties graph x))]
    [links**
     (ds/card
      {:id link-here}

      ;; header
      (dom/div {:style {:display "flex"
                        :justify-content "space-between"
                        :align-items "center"
                        :background "rgba(0,0,0,0.1)"}}

               (dom/div
                (ds/padded-1
                 {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
                 (if-let [type (rdf/resource-type graph x)]
                   (pr-type type)
                   "Resource"))

                (ds/padded-1
                 {:style {:color "#555"
                          :font-size "12px"}}
                 uri))

               (when (rdf/symbol? x)
                 (ds/padded-1
                  (load-more-button uri))))

      (when-let [name (rdf/resource-name graph x)]
        (ds/with-card-padding
          (dom/div {:style {:font-size "2em"}}
                   name)))

      (when-let [description (rdf/resource-description graph x)]
        (ds/with-card-padding description))

      (when-not (empty? lis)
        (ds/with-card-padding
          (apply
           dom/ul
           {:style {:display "flex"
                    :flex-direction "column"
                    :gap "2ex"}}
           lis))))]))

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
     #_(pr-str graph)
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

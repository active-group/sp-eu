(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]))

;; [ ] Fix links for confluences
;; [x] Load all properties
;; [x] Focus
;; [ ] Patterns for special GUIs
;; [x] Style

(def-record focus-query-action
  [focus-query-action-query])

(def-record expand-by-query-action
  [expand-by-query-action-query])

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

(defn- load-more-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?x1 . }
          WHERE { <" uri "> ?p ?x1 . }"))

(defn- load-more-button [uri]
  (dom/button
   {:style {}
    :onclick (fn [_]
               (c/return :action
                         (expand-by-query-action expand-by-query-action-query
                                                 (load-more-query uri))))}
   (if ::TODO "v" ">")))

(defn predicate-component [pred]
  (dom/strong
   (pr-predicate pred)))

(declare tree-component)

(c/defn-item property-component []
  (c/with-state-as prop
    (dom/div
     (dom/div (predicate-component (tree/property-predicate prop)))
     (dom/div {:style {:margin-left "0em"
                       :margin-top "1ex"
                       :display "flex"}}
              (c/focus tree/property-object
                       (tree-component))))))

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

(defn- node-component []
  (println "node-component")
  (c/with-state-as node
    (let [uri (tree/node-uri node)]
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
                                          (focus-query-action focus-query-action-query
                                                              (focus-query uri))))}
                             "Focus")))

       (when-let [name (node-name node)]
         (ds/with-card-padding
           (dom/div {:style {:font-size "2em"}}
                    name)))

       (when-let [description (node-description node)]
         (ds/with-card-padding description))

       (let [props (tree/node-properties node)]
         (when-not (empty? props)
           (c/focus tree/node-properties
                    (ds/with-card-padding
                      (apply
                       dom/ul
                       {:style {:display "flex"
                                :flex-direction "column"
                                :gap "2ex"}}
                       (map-indexed (fn [idx prop]
                                      (when-not (special-property? prop)
                                        (dom/li
                                         (c/focus (lens/at-index idx)
                                                  (property-component)))))
                                    props))))))))))

(defn tree-component []
  (c/with-state-as tree
    (cond
      (tree/node? tree)
      (node-component)

      (tree/literal-string? tree)
      (c/focus tree/literal-string-value
               (forms/input))

      (tree/ref? tree)
      (dom/div "REF: " (tree/ref-uri tree)))))

(c/defn-item main* []
  (c/with-state-as trees
    (apply
     dom/div
     (map-indexed (fn [idx _]
                    (c/focus (lens/at-index idx)
                             (tree-component)))
                  trees))))

(defn main [make-focus-query-action make-expand-by-query-action]
  (c/handle-action
   (c/focus tree/graph<->trees (main*))
   (fn [_ ac]
     (c/return :action
               (cond
                 (record/is-a? focus-query-action ac)
                 (make-focus-query-action (focus-query-action-query ac))

                 (record/is-a? expand-by-query-action ac)
                 (make-expand-by-query-action (expand-by-query-action-query ac))

                 :else
                 ac)))))

(defn readonly [graph make-focus-query-action make-expand-by-query-action]
  (c/isolate-state graph (main make-focus-query-action make-expand-by-query-action)))

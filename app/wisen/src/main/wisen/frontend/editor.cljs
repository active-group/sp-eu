(ns wisen.frontend.editor
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.change :as change]
            [wisen.common.change-api :as change-api]
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

(def-record delete-property [delete-property-predicate])

(c/defn-item property-component [predicate]
  (c/with-state-as obj
    (when-let [value-item (case predicate
                            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                            nil

                            "http://schema.org/name"
                            (c/focus tree/literal-string-value
                                     (forms/input {:style {:font-size "2em"
                                                           :width "100%"}}))

                            "http://schema.org/description"
                            (c/focus tree/literal-string-value
                                     (forms/textarea {:style {:width "100%"
                                                              :min-height "6em"}}))

                            (dom/div {:style {:margin-left "0em"
                                              :margin-top "1ex"
                                              :display "flex"}}
                                     (tree-component)))]
      (dom/div
       {:style {:display "flex"}}
       (dom/div
        {:style {:flex 1}}
        (dom/div (predicate-component predicate))
        value-item)
       (dom/button {:onClick (constantly
                              (c/return :action
                                        (delete-property delete-property-predicate
                                                         predicate)))}
                   "Delete")))))

(defn- focus-query [uri]
  (str "CONSTRUCT { <" uri "> ?p ?o . }
          WHERE { <" uri "> ?p ?o . }"))

(defn- node-type [node]
  (when-let [obj ((tree/node-object-for-predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type") node)]
    (when (tree/node? obj)
      (tree/node-uri obj))))

(def predicate-priority
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/keywords"])

(defn- index-of [s v]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (= v (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn- compare-predicate [p1 p2]
  (let [i1 (index-of predicate-priority p1)
        i2 (index-of predicate-priority p2)]
    (if i1
      (if i2
        (compare i1 i2)
        -1)
      (if i2
        1
        (compare p1 p2)))))

(defn- node-component []
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

       (let [props (tree/node-properties node)]
         (when-not (empty? props)

           (-> (ds/with-card-padding
                 (apply
                  dom/div
                  {:style {:display "flex"
                           :flex-direction "column"
                           :gap "2ex"}}
                  (map (fn [prop]
                         (let [pred (tree/property-predicate prop)]
                           (c/focus (tree/node-object-for-predicate pred)
                                    (property-component pred))))
                       (sort-by tree/property-predicate compare-predicate props))))

               (c/handle-action (fn [node action]
                                  (if (record/is-a? delete-property action)
                                    (let [predicate-to-delete (delete-property-predicate action)]
                                      (c/return :state (tree/node-dissoc-predicate node predicate-to-delete)))
                                    ;; else
                                    (c/return :action action)
                                    ))))))))))

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

(defn commit-changes-request [changes]
  (ajax/PUT "/api/triples"
            {:body (pr-str {:changes
                            (map change-api/change->edn changes)})
             :headers {:content-type "application/edn"}}))

(c/defn-item commit-changes [changes]
  (c/isolate-state
   nil
   (c/fragment
    (c/dynamic pr-str)
    (ajax/fetch (commit-changes-request
                 (map change/change->api changes))))))

(c/defn-item main* []
  (c/with-state-as trees
    (c/isolate-state
     ;; working trees
     trees
     (c/with-state-as working-trees
       (dom/div
        #_(pr-str (change/delta-trees trees working-trees))
        (pr-str (change/delta-trees trees working-trees))

        (c/isolate-state false
                         (c/with-state-as commit?
                           (c/fragment
                            (pr-str commit?)
                            (when commit?
                              (commit-changes (change/delta-trees trees working-trees)))
                            (dom/button {:onclick (constantly true)} "Commit changes"))))

        (dom/hr)
        (apply
         dom/div
         (map-indexed (fn [idx _]
                        (c/focus (lens/at-index idx)
                                 (tree-component)))
                      trees)))))))

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

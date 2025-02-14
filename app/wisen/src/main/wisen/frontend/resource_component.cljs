(ns wisen.frontend.resource-component
  (:require [wisen.frontend.resource :as r]
            [active.data.record :refer-macros [def-record] :refer [is-a?]]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.design-system :as ds]))

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

    p))

(defn pr-type [t]
  (case t
    "http://schema.org/GeoCoordinates"
    "The geographic coordinates of a place or event."

    "http://schema.org/GeoCircle"
    "A GeoCircle is a GeoShape representing a circular geographic area."

    t
    ))

(declare part)

(def-record delete-property [delete-property-property])

(defn property-component []
  (c/with-state-as property
    (dom/div
     {:style {:display "flex"
              :gap "16px"
              :align-items "center"
              :justify-content "space-between"}}
     (dom/div
      {:style {:flex 1 :display "flex" :flex-direction "column" :gap "8px"}}
      (dom/div (dom/strong (pr-predicate (r/property-predicate property))))
      (dom/div (c/focus r/property-object (part))))
     (dom/button {:onClick (constantly (c/return :action (delete-property delete-property-property
                                                                          property)))}
                 "Delete"))))

(def predicates
  ["http://schema.org/name"
   "http://schema.org/url"
   "http://schema.org/areaServed"
   "http://schema.org/geo"])

(defn default-object-for-predicate [pred]
  (cond
    (= pred "http://schema.org/name")
    (r/literal-string "Der gute Name")

    (= pred "http://schema.org/geo")
    (r/res
     (r/prop "@type" "http://schema.org/GeoCoordinates")
     (r/prop "http://schema.org/latitude" "0.0")
     (r/prop "http://schema.org/longitude" "0.0"))

    (= pred "http://schema.org/areaServed")
    (r/res
     (r/prop "@type" "http://schema.org/GeoCircle")
     (r/prop "http://schema.org/geoMidpoint"
             (r/res
              (r/prop "@type" "http://schema.org/GeoCoordinates")
              (r/prop "http://schema.org/latitude" "0.0")
              (r/prop "http://schema.org/longitude" "0.0")))
     (r/prop "http://schema.org/geoRadius" "1000"))

    :else
    (r/literal-string "")
    ))

(def predicate-options
  (map (fn [pred]
         (forms/option
          {:value pred}
          (pr-predicate pred)))
       predicates
       ))

(c/defn-item add-property-button []
  (c/with-state-as [resource predicate :local "http://schema.org/name"]
    (dom/div
     (c/focus lens/second
              (apply
               forms/select
               predicate-options))

     (dom/button {:onClick
                  (fn [[resource predicate] _]
                    (c/return :state [(r/assoc resource
                                               predicate
                                               (default-object-for-predicate predicate))
                                      predicate]))}
                 "Add property"))))

(defn resource-component []
  (c/with-state-as resource
    (ds/card
     ;; header
     (dom/div {:style {:display "flex"
                       :justify-content "space-between"
                       :border-bottom ds/border}}

              (ds/padded-1
               {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
               (if-let [type (r/lookup resource r/type)]
                 (pr-type type)
                 "Resource"))

              (ds/padded-1
               {:style {:color "#555"
                        :font-size "14px"}}
               (if-let [uri (r/lookup resource r/id)]
                 uri
                 "_")))
     ;; body
     (c/focus (lens/>> (r/dissoc r/id)
                       (r/dissoc r/type))
              (dom/div
               (-> (c/focus r/resource-properties
                            (c/with-state-as properties
                              (ds/with-card-padding
                                (apply dom/div

                                       (interpose (dom/hr {:style {:border-top ds/border
                                                                   :border-width "1px 0 0 0"}})
                                                  (map-indexed (fn [idx property]
                                                                 (c/focus (lens/at-index idx)
                                                                          (dom/div
                                                                           {:style {:padding "8px 0"}}
                                                                           (property-component))))
                                                               properties))))))

                   (c/handle-action (fn [resource action]
                                      (if (is-a? delete-property action)
                                        (let [property-to-delete (delete-property-property action)]
                                          (c/return :state (lens/overhaul resource
                                                                          r/resource-properties
                                                                          (fn [properties]
                                                                            (remove #{property-to-delete} properties)))))
                                        ;; else
                                        (c/return :action action)
                                        ))))

               (add-property-button))))))

(c/defn-item part []
  (c/with-state-as x
    (dom/div
     {:style {:display "flex"}}

     (dom/div
      {:style {:display "flex"}}
      (c/focus r/kind
               (forms/select
                {:style {:border ds/border
                         :border-right 0
                         :border-radius 0
                         :border-top-left-radius "4px"
                         :border-bottom-left-radius "4px"
                         :background "#ddd"
                         :padding "3px 8px"}}
                (forms/option
                 {:value r/string-literal-kind}
                 "String")
                (forms/option
                 {:value r/resource-literal-kind}
                 "Resource"))))


     (cond
       (r/literal-string? x)
       (c/focus r/literal-string-value
                (forms/input {:type "text"
                              :style {:border ds/border
                                      :border-radius 0
                                      :padding "3px 8px"}}))

       (r/resource? x)
       (resource-component)

       :else
       (throw ::unknown-part)))))


(defn main []
  (resource-component))

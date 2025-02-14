(ns wisen.frontend.resource-component
  (:require [wisen.frontend.resource :as r]
            [active.data.record :refer-macros [def-record] :refer [is-a?]]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.leaflet :as leaflet]))

#_(defn setup-map! []
  (let [mp (.map leaflet "the-map")
        tile-layer (.tileLayer leaflet "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                               #js {:maxZoom 19,
                                    :attribution "&copy; <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a>"})]
    (.setView mp #js [51, 0] 13)
    (.addTo tile-layer mp)))

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
   "http://schema.org/geo"
   "http://schema.org/description"])

(defn default-object-for-predicate [pred]
  (cond
    (= pred "http://schema.org/name")
    (r/literal-string "Der gute Name")

    (= pred "http://schema.org/geo")
    (r/res
     (r/prop "@type" "http://schema.org/GeoCoordinates")
     (r/prop "http://schema.org/latitude" "48.52105844145676")
     (r/prop "http://schema.org/longitude" "9.054090697517525"))

    (= pred "http://schema.org/areaServed")
    (r/res
     (r/prop "@type" "http://schema.org/GeoCircle")
     (r/prop "http://schema.org/geoMidpoint"
             (r/res
              (r/prop "@type" "http://schema.org/GeoCoordinates")
              (r/prop "http://schema.org/latitude" "48.52105844145676")
              (r/prop "http://schema.org/longitude" "9.054090697517525")))
     (r/prop "http://schema.org/geoRadius" "100"))

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

(defn properties-component-raw []
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

   (add-property-button)))

(defn- radius->zoom-level [radius]
  (/ 1000 radius))

(defn GeoCircle-component []
  (c/with-state-as resource
    (let [circle-lens (lens/++ (lens/>> (r/lookup "http://schema.org/geoMidpoint")
                                        (lens/++ (r/lookup "http://schema.org/latitude")
                                                 (r/lookup "http://schema.org/longitude")))
                               (r/lookup "http://schema.org/geoRadius"))
          [circle-center circle-radius] (circle-lens resource)]
      (c/focus circle-lens
               (dom/div
                (c/focus (lens/>> lens/first lens/first)
                         (dom/div
                          "Center X:"
                          (forms/input {:type "text"
                                        :style {:border ds/border
                                                :border-radius 0
                                                :padding "3px 8px"}})))

                (c/focus (lens/>> lens/first lens/second)
                         (dom/div
                          "Center Y:"
                          (forms/input {:type "text"
                                        :style {:border ds/border
                                                :border-radius 0
                                                :padding "3px 8px"}})))

                (c/focus (lens/>> lens/second)
                         (dom/div
                          "Radius:"
                          (forms/input {:type "text"
                                        :style {:border ds/border
                                                :border-radius 0
                                                :padding "3px 8px"}})))

                (leaflet/circle 
                 circle-center
                 (radius->zoom-level circle-radius)))))))

(defn properties-component [type]
  (c/with-state-as resource
    (cond
      (and (= type "http://schema.org/GeoCoordinates")
           (lens/yank resource (r/lookup "http://schema.org/latitude"))
           (lens/yank resource (r/lookup "http://schema.org/longitude")))
      (dom/div
       (properties-component-raw)
       (c/focus (lens/++ (r/lookup "http://schema.org/latitude")
                         (r/lookup "http://schema.org/longitude"))
                (leaflet/position [(lens/yank resource (r/lookup "http://schema.org/latitude"))
                                   (lens/yank resource (r/lookup "http://schema.org/longitude"))]
                                  13)))

      (and (= type "http://schema.org/GeoCircle")
           (lens/yank resource (r/lookup "http://schema.org/geoMidpoint"))
           (lens/yank resource (lens/>> (r/lookup "http://schema.org/geoMidpoint")
                                        (r/lookup "http://schema.org/latitude")))
           (lens/yank resource (lens/>> (r/lookup "http://schema.org/geoMidpoint")
                                        (r/lookup "http://schema.org/longitude")))
           (lens/yank resource (r/lookup "http://schema.org/geoRadius")))
      (GeoCircle-component)

      :else
      (properties-component-raw)
      )))

(defn resource-component []
  (c/with-state-as resource
    (let [type ((r/lookup r/type) resource)]
      (ds/card
       ;; header
       (dom/div {:style {:display "flex"
                         :justify-content "space-between"
                         :border-bottom ds/border}}

                (ds/padded-1
                 {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
                 (if (some? type)
                   (pr-type type)
                   "Resource"))

                (ds/padded-1
                 {:style {:color "#555"
                          :font-size "14px"}}
                 (if-let [uri ((r/lookup r/id) resource)]
                   uri
                   "_")))
       ;; body
       (c/focus (lens/>> (r/dissoc r/id)
                         (r/dissoc r/type))
                (properties-component type))))))

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
       (dom/div {:style {:background "red"
                         :padding 64}}
                (pr-str x))
       #_(throw ::unknown-part)))))


(defn main []
  (resource-component))

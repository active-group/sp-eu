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
      {:style {:flex 1}}
      (dom/div (pr-predicate (r/property-predicate property)))
      (dom/div (c/focus r/property-object (part))))
     (dom/button {:onClick (constantly (c/return :action (delete-property delete-property-property
                                                                          property)))}
                 "Delete"))))

(defn resource-component []
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
                           )))))

(c/defn-item part []
  (c/with-state-as x
    (dom/div
     {:style {:display "flex"}}

     (dom/div
      (c/focus r/kind
               (forms/select
                (forms/option
                 {:value r/string-literal-kind}
                 "String")
                (forms/option
                 {:value r/resource-literal-kind}
                 "Resource"))))


     (cond
       (r/literal-string? x)
       (dom/strong
        (c/focus r/literal-string-value
                 (forms/input {:type "text"})))

       (r/resource? x)
       (resource-component)))
    ))


(defn main []
  (c/with-state-as resource
    (ds/card
     (dom/div {:style {:display "flex"
                       :justify-content "space-between"
                       :border-bottom ds/border}}

              (ds/padded-1
               {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
               "Resource")

              (ds/padded-1
               {:style {:color "#555"
                        :font-size "14px"}}
               (if-let [uri (r/lookup resource r/id)]
                 uri
                 "Blank")))

     (c/focus (r/dissoc r/id)
              (resource-component)
              ))))

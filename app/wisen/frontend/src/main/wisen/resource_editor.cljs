(ns wisen.resource-editor
  (:require [wisen.resource :as r]
            [active.clojure.lens :as lens]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c-basics.forms.core :as forms]
            [active.data.record :as record]))

(c/defn-item property-item []
  (c/with-state-as property
    (cond
      (record/is-a? r/caters-to-property property)
      "CATERS TO"

      (record/is-a? r/has-website-property property)
      "HAS WEBSITE"

      )))

(c/defn-item main []
  (dom/div
   {:class "border p-2"}

   (c/isolate-state 0
                    (dom/div
                     "AJSDJASDJASJD"
                     (c/dynamic pr-str)
                     (dom/button {:onclick inc} "INC")))


   (dom/h1 "Resource")

   (dom/label "Title")
   (dom/br)
   (c/focus r/resource-title
            (forms/input {:type "text"
                          :placeholder "Resource name"}))

   (c/focus r/resource-properties
            (c/with-state-as properties
              (c/fragment
               (dom/h2 (str (count properties)) " Properties")

               (apply dom/ul
                      (map-indexed
                       (fn [i _]
                         (c/focus (lens/pos i)
                                  (dom/li
                                   (property-item))))
                       properties))

               (dom/h3 "Add property")
               (dom/button {:onclick (fn [properties]
                                       (c/return :state (conj properties r/empty-caters-to-property)))}
                           "caters to ...")
               )))))

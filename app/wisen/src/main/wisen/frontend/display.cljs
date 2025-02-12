(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.design-system :as ds]))

(defn display-object [obj]
  (cond
    (sequential? obj)
    (apply dom/ul (map display-object obj))

    (and (map? obj)
         (some? (get obj "@value")))
    (get obj "@value")

    :else
    (pr-str obj)))

(defn display-property [prop]
  (c/fragment
   (dom/dt (first prop))
   (dom/dd (display-object (second prop)))))

(defn display-resource [res]
  (ds/card
   (dom/div {:style {:display "flex"
                     :justify-content "space-between"
                     #_#_:border-bottom ds/border}}

            (ds/padded-1
             {:style {:color "hsl(229.18deg 91.04% 56.86%)"}}
             "Resource")

            (ds/padded-1
             {:style {:color "#555"
                      :font-size "14px"}}
             (get res "@id")))

   (ds/with-card-padding
     (apply dom/dl
            (map display-property
                 (dissoc res "@id"))))))

(c/defn-item display-expanded [result-json]
  (js/console.log result-json)
  (dom/div
   "TODO"
   (cond
     ;; assume a list of resources
     (array? result-json)
     (apply dom/ul
            {:style {:list-style-type "none"
                     :padding 0
                     :display "flex"
                     :flex-direction "column"
                     :gap "12px"}}

            (map (fn [res]
                   (dom/li
                    (display-resource (js->clj res))))
                 result-json))

     ;; assume a single resource
     (object? result-json)
     (display-resource (js->clj result-json)))))

(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            #_["jsonld" :as jsonld]))

(defn graph? [json]
  false
  )

(defn resource? [json]
  false)

(defn display-property [prop]
  (dom/div
   (dom/div "Predicate: " (pr-str (first prop)))
   (dom/div "Object: " (pr-str (second prop)))))

(defn display-resource [res]
  (dom/div
   (dom/div
    (dom/div "Resource Id: " (get res "@id"))
    (dom/div "Properties:"
             (apply dom/ul
                    (map (comp dom/li display-property)
                         (dissoc res "@id")))))))

(c/defn-item display-expanded [result-json]
  (js/console.log result-json)
  (dom/div
   "TODO"
   (cond
     ;; assume a list of resources
     (array? result-json)
     (apply dom/ul
            (map (fn [res]
                   (dom/li
                    (display-resource (js->clj res))))
                 result-json))

     ;; assume a single resource
     (object? result-json)
     (display-resource (js->clj result-json)))
   (pr-str result-json)))

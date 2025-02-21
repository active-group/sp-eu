(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]))

;; [ ] Fix links for confluences
;; [ ] Load all properties
;; [ ] Focus
;; [ ] Patterns for special GUIs
;; [ ] Style


(defn predicate-component [pred]
  (rdf/symbol-uri pred))

(declare node-component)

(defn property [graph links property]
  (let [[links* it] (node-component graph links (rdf/property-object property))]
    [links*
     (dom/div
      (dom/div (predicate-component (rdf/property-predicate property)))
      (dom/div {:style {:margin-left "2em"}} it))]))

(defn node-component [graph links x]
  (cond
    (or (rdf/symbol? x)
        (rdf/blank-node? x))
    (if-let [link (get links (rdf/node-uri x))]
      [links (dom/a {:href (str "#" link)} "Go #")]
      (let [link-here (rdf/symbol-uri x)
            links* (assoc links (rdf/symbol-uri x) link-here)
            [links** lis] (reduce (fn [[links its] prop]
                                    (let [[links* it] (property graph links prop)]
                                      [links* (conj its it)]))
                                  [links* []]
                                  (rdf/subject-properties graph x))]
        [links**
         (dom/div
          {:id link-here
           :style {:border "1px solid gray"
                   :padding 12}}
          (rdf/symbol-uri x)
          (dom/button {:onclick ::TODO} "Load all properties")
          (apply
           dom/ul
           lis))]))

    (rdf/literal-string? x)
    [links (rdf/literal-string-value x)]

    (rdf/collection? x)
    [links (pr-str x)]))

(defn main [graph]
  (dom/div
   (pr-str graph)
   (dom/hr)
   (apply
    dom/div
    (second (reduce (fn [[links its] x]
                      (let [[links* it] (node-component graph links x)]
                        [links* (conj its it)]))
                    [{} []]
                    (rdf/roots graph))))))

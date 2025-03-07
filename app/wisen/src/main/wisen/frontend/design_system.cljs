(ns wisen.frontend.design-system
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]))

(def border "1px solid #ddd")

(dom/defn-dom card [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {#_#_:border border
                                        :background "white"
                                        :border-radius "4px"
                                        :overflow "hidden"
                                        :box-shadow "0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24)"}})
         children))

(dom/defn-dom with-card-padding [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {:padding "8px 16px"}})
         children))

(dom/defn-dom padded-1 [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {:padding "3px 8px"}})
         children))

(dom/defn-dom padded-2 [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {:padding "6px 16px"}})
         children))

(dom/defn-dom button-primary [attrs & children]
  (apply dom/button
         (dom/merge-attributes attrs
                               {:style {:color "#3228dd"
                                        :font-weight "bold"
                                        :appearance "none"
                                        :border "none"
                                        :font-size "1em"}})
         children))

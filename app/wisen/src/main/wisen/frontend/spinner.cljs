(ns wisen.frontend.spinner
  (:require [reacl-c.dom :as dom :include-macros true]))

(dom/defn-dom main [attrs & items]
  (apply
   dom/div
   (dom/merge-attributes {:style {:display "flex"
                                  :justify-content "center"
                                  :gap "1em"
                                  :align-items "center"
                                  :font-style "italic"}}
                         attrs)
   (dom/span {:style {:width "1em"
                      :height "1em"
                      :border-top "0.3ex solid currentColor"
                      :border-right "0.3ex solid currentColor"
                      :border-bottom "0.3ex solid transparent"
                      :border-left "0.3ex solid currentColor"
                      :border-radius "50%"
                      :display "inline-block"
                      :box-sizing "border-box"
                      :animation "rotation 1s linear infinite"}})
   items))

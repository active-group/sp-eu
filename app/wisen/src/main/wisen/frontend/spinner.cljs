(ns wisen.frontend.spinner
  (:require [reacl-c.dom :as dom]))

(def main (dom/span {:style {:width "1em"
                             :height "1em"
                             :border-top "0.3ex solid currentColor"
                             :border-right "0.3ex solid currentColor"
                             :border-bottom "0.3ex solid transparent"
                             :border-left "0.3ex solid currentColor"
                             :border-radius "50%"
                             :display "inline-block"
                             :box-sizing "border-box"
                             :animation "rotation 1s linear infinite"}}))

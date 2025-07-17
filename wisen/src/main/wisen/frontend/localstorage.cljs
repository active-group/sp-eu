(ns wisen.frontend.localstorage
  (:require
   [reacl-c.core :as c :include-macros true]
   [reacl-c.dom :as dom :include-macros true]))

(c/defn-subscription get! deliver! [key]
  (deliver! (.getItem (.-localStorage js/window)
                      key))
  #())

(c/defn-effect set! [key value]
  (.setItem (.-localStorage js/window)
            key
            value))

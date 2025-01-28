(ns wisen.app
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]))

(defn init []
  (println "Hi from ClojureScript"))

(defn toplevel []
  (dom/strong "Hello!"))

(cmain/run
  (.getElementById js/document "main")
  (toplevel))

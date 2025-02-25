(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [wisen.frontend.design-system :as ds]))

;; Confluence
;; x -> y -> z
;; x -> a -> z

;; Cycle
;; x -> y
;; y -> x

(defn main []
  (ds/padded-2
   {:style {:overflow "auto"}}
   (dom/h2 "Create a new resource")
   "TODO"))

(ns wisen.frontend.create
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.osm :as osm]))

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
   #_(osm/main nil)))

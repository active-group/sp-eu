(ns wisen.frontend.create
  (:require [wisen.frontend.resource :as r]
            [wisen.frontend.resource-component :as rc]
            [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [wisen.frontend.design-system :as ds]))

(def sample-resource
  (r/res
   "http://example.org/hirsch"
   (r/prop "http://schema.org/name" "Hirsch")
   (r/prop "http://schema.org/email" "hirsch@example.org")
   (r/prop "http://schema.org/url" "https://hirsch-begegnungsstaette.de")
   (r/prop "http://schema.org/geo" (r/res
                                    nil
                                    (r/prop "@type" "http://schema.org/GeoCoordinates")
                                    (r/prop "http://schema.org/latitude" "48.52105844145676")
                                    (r/prop "http://schema.org/longitude" "9.054090697517525")))))

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
   (c/isolate-state sample-resource
                    (rc/main))))

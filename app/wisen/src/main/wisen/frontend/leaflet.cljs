(ns wisen.frontend.leaflet
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]))

(def leaflet js/L)

(c/defn-subscription setup-leaflet deliver! [ref coords zoom-level marker-position]
  (let [mp (.map leaflet (c/deref ref))
        tile-layer (.tileLayer leaflet "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                               #js {:maxZoom 19,
                                    :attribution "&copy; <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a>"})]
    (.setView mp (clj->js coords) zoom-level)
    (.addTo tile-layer mp)
    (when marker-position
      (let [marker (.marker leaflet (clj->js marker-position))]
        (.addTo marker mp)))

    (fn [_]
      (.remove mp)
      )))

(c/defn-item position [view-coords zoom-level]
  (c/with-state-as pin-coords
    (c/with-ref
      (fn [ref]
        (c/fragment
         (dom/div {:id "the-map"
                   :ref ref
                   :style {:min-height 120}})
         (setup-leaflet ref view-coords zoom-level pin-coords))))))

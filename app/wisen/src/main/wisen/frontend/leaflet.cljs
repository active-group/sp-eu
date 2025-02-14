(ns wisen.frontend.leaflet
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]))

(def leaflet js/L)

(c/defn-subscription setup-leaflet deliver! [ref coords zoom-level marker-position circle]
  (let [mp (.map leaflet (c/deref ref))
        tile-layer (.tileLayer leaflet "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                               #js {:maxZoom 19,
                                    :attribution "&copy; <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a>"})]
    (.setView mp (clj->js coords) zoom-level)
    (.addTo tile-layer mp)
    (when marker-position
      (let [marker (.marker leaflet (clj->js marker-position))]
        (.addTo marker mp)))

    (when-let [[circle-center circle-radius] circle]
      (let [crcl (.circle leaflet (clj->js circle-center) #js {:color "blue"
                                                     :radius circle-radius})]
        (.addTo crcl mp)))

    (fn [_]
      (.remove mp)
      )))

(c/defn-item circle [view-coords zoom-level]
  (c/with-state-as [circle-center circle-radius]
    (c/with-ref
      (fn [ref]
        (c/fragment
         (dom/div {:ref ref
                   :style {:min-height 240}})
         (setup-leaflet ref view-coords zoom-level nil [circle-center circle-radius]))))))

(c/defn-item position [view-coords zoom-level]
  (c/with-state-as pin-coords
    (c/with-ref
      (fn [ref]
        (c/fragment
         (dom/div {:ref ref
                   :style {:min-height 240}})
         (setup-leaflet ref view-coords zoom-level pin-coords nil))))))

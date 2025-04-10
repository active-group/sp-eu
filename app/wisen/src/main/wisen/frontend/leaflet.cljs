(ns wisen.frontend.leaflet
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [active.data.realm :as realm]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]))

(def-record bounding-box-change-action
  [bounding-box-change-action-value])

(def-record click-action
  [click-action-coordinates])

(def leaflet js/L)

(defn bounding-box->LatLngBounds [[[min-lat max-lat] [min-long max-long]]]
  (let [top-left (.latLng leaflet min-lat max-long)
        bottom-right (.latLng leaflet max-lat min-long)]
    (.latLngBounds leaflet top-left bottom-right)))

(defn LatLngBounds->bounding-box [^leaflet/LatLngBounds latLngBounds]
  (let [^leaflet/LatLng top-left (.getNorthWest latLngBounds)
        ^leaflet/LatLng bottom-right (.getSouthEast latLngBounds)]
    [[(.-lat bottom-right) (.-lat top-left)]
     [(.-lng top-left) (.-lng bottom-right)]]))

(defn LatLng->coordinates [^leaflet/LatLng latLng]
  (println (pr-str latLng))
  [(.-lat latLng)
   (.-lng latLng)])

(defn- pin-element [label color]
  (let [elem (js/document.createElement "div")]
    (set! (.-textContent elem) label)
    (set! (.-style elem)
          (str
           "border-radius: 100%;
            position: absolute;
            top: -1px;
            left: -1px;
            color: white;
            font-weight: bold;
            padding: 5px 10px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.5);
            border-top-left-radius: 0;"
           "background: " color ";"))
    elem))

(def-record pin [pin-label :- realm/string
                 pin-color :- realm/string
                 pin-coordinates :- [realm/number realm/number]])

(defn make-pin [label color coordinates]
  (pin pin-label label
       pin-color color
       pin-coordinates coordinates))

(defn pin? [this]
  (is-a? pin this))

(c/defn-subscription setup-leaflet-2 deliver! [ref view-box pins]
  (let [mp (.map leaflet (c/deref ref))
        tile-layer (.tileLayer
                    leaflet
                    "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                    #js {:maxZoom 19,
                         :attribution
                         "&copy; <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a>"})]
    (.fitBounds mp (bounding-box->LatLngBounds view-box))
    (.addTo tile-layer mp)

    (doall
     (map (fn [pin]
            (let [html (pin-element (pin-label pin)
                                    (pin-color pin))
                  marker (.marker leaflet
                                  (clj->js (pin-coordinates pin))
                                  #js {:riseOnHover true :title "Schmeitel"
                                       :icon (.divIcon leaflet #js {:html html})})]
              (.addTo marker mp)))
          pins))

    (.addEventListener mp "moveend" (fn [e]
                                      (deliver! (bounding-box-change-action
                                                 bounding-box-change-action-value
                                                 (LatLngBounds->bounding-box
                                                  (.getBounds mp))))
                                      ))

    (.addEventListener mp "click" (fn [^leaflet/MouseEvent e]
                                    (deliver! (click-action
                                               click-action-coordinates
                                               (LatLng->coordinates (.-latlng e))))
                                    ))

    (fn [_]
      (.remove mp))))

(c/defn-item main [& [attrs pins]]
  (c/with-state-as view-box
    (c/with-ref
      (fn [ref]
        (c/fragment
         (dom/div (dom/merge-attributes
                   attrs
                   {:ref ref
                    :style {:min-height 240}}))
         (c/handle-action (setup-leaflet-2 ref view-box pins)
                          (fn [_ action]
                            (cond
                              (is-a? bounding-box-change-action action)
                              (c/return :state (bounding-box-change-action-value action))

                              :else
                              (c/return :action action)
                              ))))))))

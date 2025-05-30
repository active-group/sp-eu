(ns wisen.frontend.leaflet
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [active.data.realm :as realm]
            [active.data.record :refer [is-a?] :refer-macros [def-record]]
            ["leaflet" :as leaflet]))

(def-record bounding-box-change-action
  [bounding-box-change-action-value])

(def-record click-action
  [click-action-coordinates])

(def-record setup-action
  [setup-action-map-instance])

(defn- leaflet-dom-event-stop-propagation [e]
  (.stopPropagation (.-DomEvent leaflet) e))

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
  [(.-lat latLng)
   (.-lng latLng)])

(defn- pin-element [label color href]
  (let [elem (js/document.createElement "a")]
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
    (when href
      (set! (.-href elem) href))
    elem))

(def-record pin [pin-label :- realm/string
                 pin-color :- realm/string
                 pin-coordinates :- [realm/number realm/number]
                 pin-href :- (realm/optional realm/string)])

(defn make-pin [label color coordinates & [href]]
  (pin pin-label label
       pin-color color
       pin-coordinates coordinates
       pin-href href))

(defn pin? [this]
  (is-a? pin this))

(c/defn-subscription setup-leaflet-bounds-and-pins deliver! [^leaflet/Map map-instance view-box pins]
  (let [markers (.addTo (.layerGroup leaflet) map-instance)]

    ;; view bounds
    (.fitBounds map-instance (bounding-box->LatLngBounds view-box))

    ;; pins
    (doall
     (map (fn [pin]
            (let [html (pin-element (pin-label pin)
                                    (pin-color pin)
                                    (pin-href pin))
                  marker (.marker leaflet
                                  (clj->js (pin-coordinates pin))
                                  #js {:riseOnHover true :title "Schmeitel"
                                       :icon (.divIcon leaflet #js {:html html})})]
              (.addLayer markers marker)))
          pins))

    (fn []
      (.removeLayer map-instance markers))))

(letfn [(setup! [deliver! elem]
          (let [mp (.map leaflet elem)
                tile-layer (.tileLayer
                            leaflet
                            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                            #js {:maxZoom 19,
                                 :attribution
                                 "&copy; <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a>"})]
            (.addTo tile-layer mp)

            (.addEventListener mp "moveend" (fn [e]
                                              (leaflet-dom-event-stop-propagation e)
                                              (deliver! (bounding-box-change-action
                                                         bounding-box-change-action-value
                                                         (LatLngBounds->bounding-box
                                                          (.getBounds mp))))))

            (.addEventListener mp "dblclick" (fn [^leaflet/MouseEvent e]
                                               (leaflet-dom-event-stop-propagation e)
                                               (deliver! (click-action
                                                          click-action-coordinates
                                                          (LatLng->coordinates (.-latlng e))))))

            (.setTimeout js/window
                         #(deliver! (setup-action setup-action-map-instance mp))))
          )]

  (c/defn-subscription setup-leaflet deliver! [ref ^leaflet/Map map-instance]
    (let [elem (c/deref ref)]
      (cond
        (nil? map-instance)
        (setup! deliver! elem)

        (not= (c/deref ref)
              (.getContainer map-instance))
        (do
          (.remove map-instance)
          (setup! deliver! elem))))

    #()))

(c/defn-item main [& [attrs pins]]
  (c/with-state-as [view-box map-instance :local nil]
    (c/with-ref
      (fn [ref]
        (c/fragment

         (dom/div (dom/merge-attributes
                   attrs
                   {:ref ref
                    :style {:min-height 240}}))
         
         (c/cleanup (fn [_]
                      (when map-instance
                        (.remove map-instance))
                      (c/return)))
         
         (c/handle-action (c/fragment (setup-leaflet ref map-instance)
                                      (when map-instance
                                        (setup-leaflet-bounds-and-pins map-instance view-box pins)))
                          (fn [[st ls] action]
                            (cond
                              (is-a? bounding-box-change-action action)
                              (c/return :state [(bounding-box-change-action-value action) ls])

                              (is-a? setup-action action)
                              (c/return :state [st (setup-action-map-instance action)])

                              :else
                              (c/return :action action)
                              ))))))))

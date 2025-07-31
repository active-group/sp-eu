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

(defn- pin-element [label hue href]
  (let [elem (js/document.createElement "a")
        gradient-id (str "gradient" hue)
        steel-gradient-id (str "steel" hue)
        highlight-gradient-1 (str "highlight-1-" hue)
        highlight-gradient-2 (str "highlight-2-" hue)]
    (set! (.-innerHTML elem)
          (str
           "<svg viewBox=\"0 0 100 100\" xmlns=\"http://www.w3.org/2000/svg\">
<defs>
        <radialGradient cx=\"25.9069462%\" cy=\"35.1059715%\" fx=\"25.9069462%\" fy=\"35.1059715%\" r=\"71.0927612%\" id=\"" gradient-id "\">
            <stop stop-color=\"hsl(" hue "," 100 "%," 94 "%) \" offset=\"0%\"></stop>
            <stop stop-color=\"hsl(" hue "," 100 "%," 54 "%)\" offset=\"26.3791275%\"></stop>
            <stop stop-color=\"hsl(" hue "," 100 "%," 20 "%) \" offset=\"100%\"></stop>
        </radialGradient>

        <linearGradient x1=\"0%\" y1=\"50%\" x2=\"100%\" y2=\"50%\" id=\"" steel-gradient-id "\">
            <stop stop-color=\"#636363\" offset=\"0%\"></stop>
            <stop stop-color=\"#E0E0E0\" offset=\"29.2526777%\"></stop>
            <stop stop-color=\"#747474\" offset=\"33.9380305%\"></stop>
            <stop stop-color=\"#494949\" offset=\"44.1131735%\"></stop>
            <stop stop-color=\"#E1E1E1\" offset=\"44.7838181%\"></stop>
            <stop stop-color=\"#FFFFFF\" offset=\"53.9940693%\"></stop>
            <stop stop-color=\"#535353\" offset=\"55.3414956%\"></stop>
            <stop stop-color=\"#8F8F8F\" offset=\"83.7080252%\"></stop>
            <stop stop-color=\"#656565\" offset=\"100%\"></stop>
        </linearGradient>

        <radialGradient cx=\"69.0158708%\" cy=\"79.1982357%\" fx=\"69.0158708%\" fy=\"79.1982357%\" r=\"19.5027521%\" gradientTransform=\"translate(0.690159,0.791982),rotate(50.286907),scale(1.000000,2.012987),translate(-0.690159,-0.791982)\" id=\"" highlight-gradient-1 "\">
            <stop stop-color=\"#FFE1E1\" offset=\"0%\"></stop>
            <stop stop-color=\"#FFFFFF\" stop-opacity=\"0\" offset=\"80.5506993%\"></stop>
            <stop stop-color=\"#000000\" stop-opacity=\"0\" offset=\"100%\"></stop>
            <stop stop-color=\"#FFFFFF\" stop-opacity=\"0\" offset=\"100%\"></stop>
        </radialGradient>

        <radialGradient cx=\"69.0158708%\" cy=\"79.1982357%\" fx=\"69.0158708%\" fy=\"79.1982357%\" r=\"19.5027521%\" gradientTransform=\"translate(0.690159,0.791982),rotate(50.286907),scale(1.000000,2.012987),translate(-0.690159,-0.791982)\" id=\"" highlight-gradient-2 "\">
            <stop stop-color=\"#FFE1E1\" offset=\"0%\"></stop>
            <stop stop-color=\"#FFFFFF\" stop-opacity=\"0\" offset=\"26.3791275%\"></stop>
            <stop stop-color=\"#000000\" stop-opacity=\"0\" offset=\"100%\"></stop>
            <stop stop-color=\"#FFFFFF\" stop-opacity=\"0\" offset=\"100%\"></stop>
        </radialGradient>
</defs>
  <filter x=\"-50%\" y=\"-50%\" width=\"200%\" height=\"200%\" id=\"blurry\"><feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"6\" /></filter>

  <svg filter=\"url(#blurry)\" viewBox=\"0 0 100 100\" x=\"0\" y=\"0\" width=\"100\" height=\"100\">
      <line x1=\"50\" y1=\"50\" x2=\"70\" y2=\"50\" stroke=\"#555\" stroke-width=\"6\" stroke-linecap=\"round\" />
      <circle cx=\"70\" cy=\"50\" r=\"8\" fill=\"#555\" />
  </svg>

  <rect x=49 y=20 width=2 height=30 fill=\"url(#" steel-gradient-id ")\" />
  <!-- <circle cx=\"20\" cy=\"20\" r=\"20\" fill=\"#fff\"/> -->
  <circle cx=\"50\" cy=\"20\" r=\"12\" fill=\"url(#" gradient-id ")\"/>
  <circle cx=\"50\" cy=\"20\" r=\"12\" opacity=\"0.272553943\" fill=\"url(#" highlight-gradient-1 ")\"/>
  <circle cx=\"50\" cy=\"20\" r=\"12\" opacity=\"0.2\" fill=\"url(#" highlight-gradient-2 ")\"/>
</svg>
"))
    #_(set! (.-textContent elem) label)
    (set! (.-style elem)
          (str
           "display: block;"
           "position: absolute;"
           "bottom: -60px;"
           "left: -60px;"
           "width: 120px;"
           "height: 120px;"))
    (when href
      (set! (.-href elem) href))
    elem))

(def-record pin [pin-label :- realm/string
                 pin-hue :- realm/number
                 pin-coordinates :- [realm/number realm/number]
                 pin-href :- (realm/optional realm/string)])

(defn make-pin [label hue coordinates & [href]]
  (pin pin-label label
       pin-hue hue
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
                                    (pin-hue pin)
                                    (pin-href pin))
                  marker (.marker leaflet
                                  (clj->js (pin-coordinates pin))
                                  #js {:riseOnHover true
                                       :icon (.divIcon leaflet #js {:html html
                                                                    ;; This must be set else leaflet adds a class
                                                                    ;; that adds an ugly white background color
                                                                    :className ""})})]
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

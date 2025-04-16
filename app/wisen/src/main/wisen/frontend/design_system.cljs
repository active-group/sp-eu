(ns wisen.frontend.design-system
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]))

(def border "1px solid gray")

(dom/defn-dom card [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {#_#_:border border
                                        :background "white"
                                        :border-radius "4px"
                                        :overflow "hidden"
                                        :box-shadow "0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24)"}})
         children))

(dom/defn-dom with-card-padding [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {:padding "8px 16px"}})
         children))

(dom/defn-dom padded-1 [attrs & children]
  (apply dom/div
         (dom/merge-attributes attrs
                               {:style {:padding "3px 8px"}})
         children))

(dom/defn-dom padded-2 [attrs & children]
  (apply dom/div
         (dom/merge-attributes {:style {:padding "6px 16px"}}
                               attrs)
         children))

(dom/defn-dom button-primary [attrs & children]
  (apply dom/button
         (dom/merge-attributes {:style {:color "#3228dd"
                                        :background "none"
                                        :font-weight "bold"
                                        :appearance "none"
                                        :border "none"
                                        :font-size "1em"
                                        :padding 0
                                        :cursor "pointer"}}
                               attrs)
         children))

(dom/defn-dom button-secondary [attrs & children]
  (apply dom/button
         (dom/merge-attributes {:style {:color "#111"
                                        :background "none"
                                        :appearance "none"
                                        :border "none"
                                        :font-size "1em"
                                        :padding 0
                                        :cursor "pointer"}}
                               attrs)
         children))

;;

(c/defn-effect focus! [ref]
  (when-let [elem (c/deref ref)]
    (.focus elem)))

(defn- x+focus [x attrs children]
  (c/with-ref
    (fn [ref]
      (c/fragment
       (-> (c/focus lens/first
                    (apply
                     x
                     (dom/merge-attributes
                      attrs
                      {:ref ref
                       :onBlur (constantly (c/return :action false))
                       :onFocus (constantly (c/return :action true))})
                     children))
           (c/handle-action (fn [[st _] ac]
                              [st ac])))
       (c/focus lens/second
                (c/once (fn [should-focus?]
                          (if should-focus?
                            (c/return :action (focus! ref))
                            (c/return))
                          )))))))

(dom/defn-dom select [attrs & children]
  (apply forms/select
         (dom/merge-attributes
          {:style {:background-color "#f0f0f0"
                   :border "1px solid #888"
                   :padding "4px 8px"
                   :font-size "14px"
                   :color "#333"
                   :border-radius "3px"}}
          attrs)
         children))

(dom/defn-dom select+focus [attrs & children]
  (x+focus select attrs children))

(dom/defn-dom input [attrs & children]
  (let [disabled? (get attrs :disabled)]
    (apply forms/input
           (dom/merge-attributes {:style
                                  {:background-color (if disabled? "#eee" "#fefefe")
                                   :border (if disabled? "1px solid #bbb" "1px solid #888")
                                   :padding "4px 8px"
                                   :font-size "14px"
                                   :color "#333"
                                   :border-radius "3px"}}
                                 attrs)
           children)))

(dom/defn-dom input+focus [attrs & children]
  (x+focus input attrs children))

(dom/defn-dom textarea [attrs & children]
  (let [disabled? (get attrs :disabled)]
    (apply forms/textarea
           (dom/merge-attributes {:style
                                  {:background-color (if disabled? "#eee" "#fefefe")
                                   :border (if disabled? "1px solid #bbb" "1px solid #888")
                                   :padding "4px 8px"
                                   :font-size "14px"
                                   :color "#333"
                                   :border-radius "3px"}}
                                 attrs)
           children)))

(dom/defn-dom textarea+focus [attrs & children]
  (x+focus textarea attrs children))

(def plus-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/circle
    {:cx "8"
     :cy "8"
     :r "6"
     :fill "currentColor"})
   (dom/path
    {:d "M8 5V11M5 8H11"
     :stroke "white"
     :strokeWidth "2"
     :strokeLinecap "round"
     :strokeLinejoin "round"})))

(def minus-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/circle
    {:cx "8"
     :cy "8"
     :r "6"
     :fill "currentColor"})
   (dom/path
    {:d "M5 8H11"
     :stroke "white"
     :strokeWidth "2"
     :strokeLinecap "round"
     :strokeLinejoin "round"})))

(def dot-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/path
    {:d "M8 4L14 14H2L8 4Z"
     :fill "currentColor"})))

(def search-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/path
    {:d "M7.2 12.8a5.6 5.6 0 1 0 0-11.2 5.6 5.6 0 0 0 0 11.2z"
     :stroke "currentColor"
     :strokeWidth "1.5"
     :strokeLinecap "round"
     :strokeLinejoin "round"})
   (dom/path
    {:d "m14 14-3-3"
     :stroke "currentColor"
     :strokeWidth "1.5"
     :strokeLinecap "round"
     :strokeLinejoin "round"})))

(def x-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/path
    {:d "M4 4L12 12M4 12L12 4"
     :stroke "currentColor"
     :strokeWidth "2"
     :strokeLinecap "round"
     :strokeLinejoin "round"})))

;; License: Apache. Made by vaadin: https://github.com/vaadin/vaadin-icons
(defn lightbulb-icon [& [size]]
  (dom/svg
   {:viewBox "0 0 16 16"
    :width (or size "16")
    :height (or size "16")
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"
    :xmlnsXlink "http://www.w3.org/1999/xlink"}
   (dom/path
    {:d "M8 0c-2.761 0-5 2.239-5 5 0.013 1.672 0.878 3.138 2.182 3.989l0.818 2.011c-0.276 0-0.5 0.224-0.5 0.5s0.224 0.5 0.5 0.5c-0.276 0-0.5 0.224-0.5 0.5s0.224 0.5 0.5 0.5c-0.276 0-0.5 0.224-0.5 0.5s0.224 0.5 0.5 0.5c-0.276 0-0.5 0.224-0.5 0.5s0.224 0.5 0.5 0.5h0.41c0.342 0.55 0.915 0.929 1.581 0.999 0.684-0.071 1.258-0.449 1.594-0.99l0.415-0.009c0.276 0 0.5-0.224 0.5-0.5s-0.224-0.5-0.5-0.5c0.276 0 0.5-0.224 0.5-0.5s-0.224-0.5-0.5-0.5c0.276 0 0.5-0.224 0.5-0.5s-0.224-0.5-0.5-0.5c0.276 0 0.5-0.224 0.5-0.5s-0.224-0.5-0.5-0.5l0.8-2c1.322-0.862 2.187-2.328 2.2-3.998 0-2.763-2.239-5.002-5-5.002zM10.25 8.21l-0.25 0.17-0.11 0.29-0.89 2.14c-0.042 0.111-0.147 0.189-0.27 0.19h-1.51c-0.103-0.020-0.186-0.093-0.219-0.188l-0.871-2.142-0.13-0.29-0.25-0.18c-1.045-0.7-1.729-1.868-1.75-3.197-0-2.212 1.791-4.003 4-4.003s4 1.791 4 4c-0.017 1.336-0.702 2.509-1.736 3.201z"
     :fill "currentColor"})
   (dom/path
    {:d "M10.29 3c-0.574-0.612-1.387-0.995-2.289-1l-0.001 1c0.585 0.002 1.115 0.238 1.5 0.62 0.278 0.386 0.459 0.858 0.499 1.37l1.001 0.009c-0.045-0.756-0.305-1.443-0.718-2.011z"
     :fill "currentColor"})))


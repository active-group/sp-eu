(ns wisen.frontend.forms
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [active.data.record :refer-macros [def-record] :refer [is-a?]]
            [active.data.realm :as realm]
            [reacl-c-basics.forms.core :as forms]))

(def-record selection
  [selection-start
   selection-end])

(defn make-selected [& [start end]]
  (selection selection-start (or start 0)
             selection-end (or end start 0)))

(defn selected? [x]
  (is-a? selection x))

(def unselected nil)

(def unselected? nil?)

(def selection-info
  (realm/optional selection))

;;

(defn- adjust! [elem sel]
  (if (selected? sel)
    (do
      (.setSelectionRange elem
                          (selection-start sel)
                          (selection-end sel))
      
      (.focus elem))
    (.blur elem)))

(defn- current-selection-info! [elem]
  (if (= elem (.-activeElement js/document))
    (let [start (.-selectionStart elem)
          end (.-selectionEnd elem)]
      (selection selection-start start
                 selection-end end))
    ;; else
    unselected))

(c/defn-effect reconcile! [elem sel]
  (let [current-sel (current-selection-info! elem)]
    (when (not= current-sel sel)
      (adjust! elem sel))))

(c/defn-subscription selection-sub deliver! [ref]
  (if-let [elem (c/deref ref)]
    (let [handler (fn [ev]
                    (let [target (.-target ev)]
                      (when (= target (c/deref ref))
                        (deliver! ev))))]
      (.addEventListener js/window "selectionchange" handler)
      #(.removeEventListener js/window "selectionchange" handler))
    #()))

(defn x+ [x attrs & children]
  (c/with-state-as state
    (let [value (first state)
          sel-info (second state)
          handler (fn [st ev]
                    (let [target (.-target ev)
                          value (.-value (.-target ev))]
                      [value (current-selection-info! target)]))]
      (c/with-ref
        (fn [ref]
          (c/fragment

           (apply
            x
            (dom/merge-attributes
             attrs
             {:ref ref
              :value value
              :onChange handler
              :onFocus handler
              :onBlur handler})
            children)

           (when (is-a? selection sel-info)
             (-> (selection-sub ref)
                 (c/handle-action handler)))

           (c/focus lens/second
                    (c/once (fn [sel-info]
                              (if-let [elem (c/deref ref)]
                                (c/return :action (reconcile! elem sel-info))
                                (c/return)))))))))))

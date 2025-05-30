(ns wisen.frontend.forms
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [active.data.record :refer-macros [def-record] :refer [is-a?]]
            [active.data.realm :as realm]
            [reacl-c-basics.forms.core :as forms]))

(def-record selected [])

(def-record text-selection
  [text-selection-start
   text-selection-end])

(defn make-selected [& [start end]]
  (text-selection text-selection-start (or start 0)
                  text-selection-end (or end start 0)))

(defn selected? [x]
  (or (is-a? selected x)
      (is-a? text-selection x)))

(def unselected nil)

(def unselected? nil?)

(def selection-info
  (realm/optional (realm/union
                   selected
                   text-selection)))

(defn selection-simplify
  ([sel]
   (cond
     (is-a? text-selection sel)
     (selected)

     (is-a? selected sel)
     sel

     (unselected? sel)
     sel))
  ([sel new-sel]
   new-sel))

;;

(defn- adjust! [elem sel]
  (cond
    (is-a? text-selection sel)
    (do
      (.setSelectionRange elem
                          (text-selection-start sel)
                          (text-selection-end sel))
      
      (.focus elem))

    (is-a? selected sel)
    (.focus elem)

    (unselected? sel)
    (.blur elem)))

(defn- current-selection-info! [elem]
  (if (= elem (.-activeElement js/document))
    (let [start (.-selectionStart elem)
          end (.-selectionEnd elem)]
      (if (and start end)
        (text-selection text-selection-start start
                        text-selection-end end)
        (selected)))
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

           (when (selected? sel-info)
             (-> (selection-sub ref)
                 (c/handle-action handler)))

           (c/focus lens/second
                    (c/once (fn [sel-info]
                              (if-let [elem (c/deref ref)]
                                (c/return :action (reconcile! elem sel-info))
                                (c/return)))))))))))

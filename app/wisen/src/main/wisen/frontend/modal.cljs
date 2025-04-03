(ns wisen.frontend.modal
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [active.data.record :as record :refer-macros [def-record]]))

(c/defn-effect show-modal [ref]
  (when-let [e (c/deref ref)]
    (.showModal e)))

(c/defn-subscription close-modal-sub deliver! [ref close-action]
  (let [e (c/deref ref)
        handler (fn [e]
                  (deliver! close-action))]
    (.addEventListener e "close" handler)
    #(.removeEventListener e "close" handler)))

(dom/defn-dom padded [attrs & items]
  (apply dom/div
         (dom/merge-attributes
          {:style {:padding "8px 16px"}}
          attrs)
         items))

(dom/defn-dom toolbar [attrs & items]
  (apply padded
         (dom/merge-attributes
          {:style {:border-top "1px solid #888"
                   :display "flex"
                   :justify-content "space-between"}}
          attrs)
         items))

(dom/defn-dom main [attrs close-action & items]
  (c/with-ref
    (fn [ref]
      (c/fragment

       (apply
        dom/h
        "dialog"
        (dom/merge-attributes {:ref ref
                               :style {:min-width "70%"
                                       :padding 0
                                       :margin "0 auto"
                                       :border "1px solid #888"
                                       :border-top 0
                                       :box-shadow "0 2px 16px rgba(0,0,0,0.4)"
                                       :border-bottom-right-radius "5px"
                                       :border-bottom-left-radius "5px"
                                       }}
                              attrs)
        items)

       ;; open modal upon first render
       (c/once (fn [_]
                 (c/return :action (show-modal ref))))

       ;; listen for close event
       (close-modal-sub ref close-action)
       ))))

(c/defn-item modal-button [title item-f]
  (c/with-state-as [state show? :local false]
    (c/fragment

     (c/focus lens/second
              (ds/button-primary {:onClick (constantly true)} title))

     (when show?
       (-> (main
            ::close-action
            (c/focus lens/first (item-f ::close-action)))

           (c/handle-action (fn [[state local-state] ac]
                              (if (= ::close-action ac)
                                (c/return :state [state false])
                                (c/return :action ac)))))))))

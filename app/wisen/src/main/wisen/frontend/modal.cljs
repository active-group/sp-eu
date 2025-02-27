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

(dom/defn-dom main [attrs close-action & items]
  (c/with-ref
    (fn [ref]
      (c/fragment

       (apply
        dom/h
        "dialog"
        (assoc attrs :ref ref)
        items)

       ;; open modal upon first render
       (c/once (fn [_]
                 (c/return :action (show-modal ref))))

       ;; listen for close event
       (close-modal-sub ref close-action)
       ))))

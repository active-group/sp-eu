(ns wisen.frontend.details
  (:require
   [reacl-c.core :as c :include-macros true]
   [reacl-c.dom :as dom :include-macros true]
   [active.clojure.lens :as lens]))

(c/defn-subscription details-open deliver! [ref]
  (let [elem (c/deref ref)
        handler (fn [event]
                  (let [elem (.-target event)]
                    (deliver! (.-open elem))))]
    (.addEventListener elem "toggle" handler)
    #(.removeEventListener elem "toggle" handler)
    ))

(dom/defn-dom details [attrs & children]
  (c/with-state-as [state open?]
    (c/with-ref
      (fn [ref]
        (c/fragment
         (apply dom/details
                (dom/merge-attributes attrs
                                      {:ref ref
                                       :open open?})
                children)
         (c/focus lens/second
                  (c/handle-action (details-open ref)
                                   (fn [_ new-open?] new-open?))))))))

(dom/defn-dom summary [attrs & children]
  (apply dom/summary attrs children))

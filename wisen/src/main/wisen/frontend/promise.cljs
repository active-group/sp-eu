(ns wisen.frontend.promise
  (:require [reacl-c.core :as c :include-macros true]
            [active.clojure.lens :as lens]))

(c/defn-subscription promise-await deliver! [promise]
  (.then promise deliver!)
  #())

(c/defn-item call-with-promise-result [promise k]
  (c/with-state-as [state result :local nil]
   (c/fragment
    (c/focus lens/second
             (c/handle-action
              (promise-await promise)
              (fn [_ result] result)))

    (when result
      (c/focus lens/first (k result))))))

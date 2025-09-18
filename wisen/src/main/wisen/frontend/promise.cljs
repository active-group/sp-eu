(ns wisen.frontend.promise
  (:require [reacl-c.core :as c :include-macros true]
            [active.clojure.lens :as lens]))

(c/defn-subscription promise-await deliver! [promise]
  (.catch
   (.then promise
          (fn [v]
            (deliver! [:success v])))
   (fn [e]
     (deliver! [:error e])))
  #())

(c/defn-item call-with-promise-result [promise k & [err-k]]
  (c/with-state-as [state result :local nil]
   (c/fragment
    (c/focus lens/second
             (c/handle-action
              (promise-await promise)
              (fn [_ result]
                result)))

    (when result
      (case (first result)
        :success
        (c/focus lens/first (k (second result)))
        :error
        (when err-k
          (c/focus lens/first (err-k (second result)))))))))

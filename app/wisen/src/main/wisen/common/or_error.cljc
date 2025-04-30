(ns wisen.common.or-error
  (:require #?(:cljs [active.data.record :as record :refer-macros [def-record]])
            #?(:clj [active.data.record :as record :refer [def-record]])))

(def-record success
  [success-value])

(defn make-success [v]
  (success success-value v))

(defn success? [x]
  (record/is-a? success x))

(def-record error
  [error-value])

(defn make-error [v]
  (error error-value v))

(defn error? [x]
  (record/is-a? error x))

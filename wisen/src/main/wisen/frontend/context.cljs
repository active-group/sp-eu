(ns wisen.frontend.context
  (:require [wisen.frontend.translations :as tr]
            [active.data.record :refer-macros [def-record]]))

(def-record context
  [language :- tr/language
   schema])

(defn make [lang sch]
  (context
   language lang
   schema sch))

(defn text [ctx symb]
  (symb (language ctx)))

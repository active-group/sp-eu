(ns wisen.frontend.context
  (:require [wisen.frontend.translations :as tr]
            [active.data.realm :as realm]
            [active.data.record :refer-macros [def-record]]))

(def-record context
  [language :- tr/language
   schema
   commit-id :- realm/string])

(defn make [lang sch comm-id]
  (context
   language lang
   schema sch
   commit-id comm-id))

(defn text [ctx symb]
  (symb (language ctx)))

(defn text-fn [ctx f & args]
  (apply f (language ctx) args))

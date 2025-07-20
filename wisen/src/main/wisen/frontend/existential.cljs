(ns wisen.frontend.existential
  (:require [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(def existential realm/integer)

(defn existential? [x]
  (realm/contains? existential x))

(defn coerce-existential [uri]
  (if (existential? uri)
    uri
    (hash uri)))

(defn string->existential [s]
  (println "trying")
  (println (pr-str s))
  (when (re-matches #"^[0-9]+$" s)
    (println "yep")
    (js/parseInt s)))










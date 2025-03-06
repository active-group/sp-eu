(ns wisen.frontend.schemaorg
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(def hide-predicate #{"http://www.w3.org/1999/02/22-rdf-syntax-ns#type"})

(def ^:private predicate-priority
  ["http://schema.org/name"
   "http://schema.org/description"
   "http://schema.org/keywords"
   "http://schema.org/location"
   "http://schema.org/sameAs"

   "http://schema.org/streetAddress"
   "http://schema.org/postalCode"
   "http://schema.org/addressLocality"
   "http://schema.org/addressCountry"

   "http://schema.org/openingHoursSpecification"

   "http://schema.org/email"

   "http://schema.org/dayOfWeek"
   "http://schema.org/opens"
   "http://schema.org/closes"

   ])

(defn- index-of [s v]
  (loop [idx 0 items s]
    (cond
      (empty? items) nil
      (= v (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn compare-predicate [p1 p2]
  (let [i1 (index-of predicate-priority p1)
        i2 (index-of predicate-priority p2)]
    (if i1
      (if i2
        (compare i1 i2)
        -1)
      (if i2
        1
        (compare p1 p2)))))

(def default-predicate
  "http://schema.org/name")

(ns wisen.frontend.resource
  (:refer-clojure :exclude [dissoc])
  (:require [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as r]
            [active.clojure.lens :as lens]))

;; Some predicates

(def id ::id)

;; ---

(defn literal-string? [x]
  (string? x))

(def literal-string-value lens/id)

(declare resource)

(def-record property
  [property-predicate :- (r/union r/string
                                  (r/enum id))

   property-object :- (r/union r/string
                               (r/delay resource))])

(defn prop [p o]
  (property
   property-predicate p
   property-object o))

(defn property? [x]
  (record/is-a? property x))

(def-record resource
  [resource-properties :- (r/sequence-of property)])

(defn res [& props]
  (resource resource-properties props))

(defn resource? [x]
  (record/is-a? resource x))

(defn lookup [r pred]
  (some (fn [prop]
          (when (= pred (property-predicate prop))
            (property-object prop)))
        (resource-properties r)))

(defn dissoc [pred]
  (fn
    ([r]
     (resource
      resource-properties
      (remove #(= pred (property-predicate %))
              (resource-properties r))))
    ([_ r]
     r)))

(def string-literal-kind ::string)
(def resource-literal-kind ::resource)

(defn default-value-for-kind [kind]
  (cond
     (= kind string-literal-kind)
     ""

     (= kind resource-literal-kind)
     (res (prop "predicate" "object"))))

(defn kind
  ([value]
   (cond
     (literal-string? value)
     string-literal-kind

     (resource? value)
     resource-literal-kind))
  ([value new-kind]
   (if (= (kind value) new-kind)
     ;; keep as-is
     value
     ;; change to new default
     (default-value-for-kind new-kind))))

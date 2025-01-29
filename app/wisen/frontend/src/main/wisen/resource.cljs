(ns wisen.resource
  (:require [active.data.record :refer-macros [def-record]]
            [active.data.realm :as realm]))

(def-record caters-to-property
  [caters-to-property-group :- (realm/enum :elderly :queer :immigrant)])

(def empty-caters-to-property
  (caters-to-property caters-to-property-group :elderly))

(def-record has-website-property
  [has-website-property-website :- realm/string])

(def decimal
  ;; x.y
  (realm/tuple realm/integer realm/integer))

(def coordinates
  ;; long, lat
  (realm/tuple decimal decimal))

(def-record placed-at-property
  [placed-at-property-coordinates :- coordinates])

(def property (realm/union caters-to-property))

(def-record resource
  [resource-title :- realm/string
   resource-properties :- (realm/sequence-of property)])

(def empty-resource
  (resource
   resource-title "New Resource"
   resource-properties []))

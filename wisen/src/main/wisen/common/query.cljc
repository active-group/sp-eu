(ns wisen.common.query
  (:require 
   [active.data.record :as record :refer-macros [def-record]]
   [active.data.realm :as realm]
   [active.clojure.lens :as lens]
   [clojure.string :as string]))

(def-record geo-bounding-box
  [geo-bounding-box-min-lat :- realm/number
   geo-bounding-box-max-lat :- realm/number
   geo-bounding-box-min-lon :- realm/number
   geo-bounding-box-max-lon :- realm/number])

(def geo-bounding-box<->vectors
  (lens/xmap
   (fn [gbb]
     [[(geo-bounding-box-min-lat gbb)
       (geo-bounding-box-max-lat gbb)]
      [(geo-bounding-box-min-lon gbb)
       (geo-bounding-box-max-lon gbb)]])
   (fn [[[min-lat max-lat] [min-lon max-lon]]]
     (geo-bounding-box
      geo-bounding-box-min-lat min-lat
      geo-bounding-box-max-lat max-lat
      geo-bounding-box-min-lon min-lon
      geo-bounding-box-max-lon max-lon))))

(def initial-geo-bounding-box
  (geo-bounding-box
   geo-bounding-box-min-lon 9.0051
   geo-bounding-box-max-lon 9.106
   geo-bounding-box-min-lat 48.484
   geo-bounding-box-max-lat 48.550))

(def organization-type :organization)
(def event-type :event)
(def offer-type :offer)

(def thing-type
  (realm/enum
   organization-type
   event-type
   offer-type))

(def initial-thing-type-filter
  #{organization-type})

(def elderly-target-group :elderly)
(def queer-target-group :queer)
(def immigrants-target-group :immigrants)

(def target-group
  (realm/enum
   elderly-target-group
   queer-target-group
   immigrants-target-group))

(def initial-target-group-filter
  #{elderly-target-group
    queer-target-group
    immigrants-target-group})

(def-record query
  [query-geo-bounding-box :- geo-bounding-box
   query-fuzzy-search-term :- realm/string
   query-filter-thing-type :- (realm/optional (realm/set-of thing-type))
   query-filter-target-group :- (realm/optional (realm/set-of target-group))])

(def initial-query
  (query query-geo-bounding-box initial-geo-bounding-box
         query-fuzzy-search-term ""))

(defn everything-query? [query]
  (and (string/blank? (query-fuzzy-search-term query))
       (nil? (query-filter-target-group query))
       (nil? (query-filter-thing-type query))))

(defn serialize-query [query]
  "TODO")

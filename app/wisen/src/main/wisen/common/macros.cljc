(ns wisen.common.macros
  (:require [active.data.record :as record])
  #?(:cljs
     (:require ["rdflib" :as rdflib])))

(defmacro define-rdf-datatype [?name ?xml ?constructor ?predicate ?value-accessor]
  `(do

     (def ~?name (rdflib/namedNode ~?xml))

     (defn ~?constructor [x#]
       (rdflib/literal x# ~?name))

     (defn ~?predicate [x#]
       (and
        (instance? rdflib/Literal x#)
        (.equals goog.object
                 (.-datatype x#)
                 ~?name)))

     (defn ~?value-accessor [x#]
       (.-value x#))

     ))

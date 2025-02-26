(ns wisen.frontend.osm
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.design-system :as ds]))

(defn osm-lookup-request [osm-id]
  (ajax/GET (str "/osm/lookup/" osm-id)
            {:headers {:accept "application/ld+json"}}))

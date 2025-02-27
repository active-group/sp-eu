(ns wisen.frontend.osm
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.tree :as tree]
            [wisen.common.vocabulary :as vocabulary]
            [clojure.string :as str]))

(defn osm-lookup-request [osm-id]
  (ajax/GET (str "/osm/lookup/" osm-id)
            {:headers {:accept "application/ld+json"}}))

(defn node-osm-uri [node]
  (let [obj ((tree/node-object-for-predicate vocabulary/wisen-osm-uri-predicate) node)]
    (when (tree/literal-string? obj)
      (let [s (tree/literal-string-value obj)]
        (when (str/starts-with? s "https://www.openstreetmap.org/")
          s)))))

(defn organization-do-link-osm [organization-node osm-id osm-place-node]
  (lens/overhaul organization-node
                 tree/node-properties
                 (fn [old-properties]
                   (-> old-properties
                       (conj (tree/make-property "http://schema.org/location" osm-place-node))
                       (conj (tree/make-property vocabulary/wisen-osm-uri-predicate (tree/make-literal-string osm-id)))))))

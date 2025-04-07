(ns wisen.frontend.osm
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.edit-tree-2 :as edit-tree]
            [wisen.common.vocabulary :as vocabulary]
            [clojure.string :as str]))

(defn- osm-uri->osm-id [osm-uri]
  (let [type-slash-id (subs osm-uri (count "https://www.openstreetmap.org/"))
        l (str/split type-slash-id #"/|#")
        type (first l)
        id (second l)]
    (str
     (case type
           "node" "N"
           "way" "W"
           "relation" "R")
     id)))

(osm-uri->osm-id "https://www.openstreetmap.org/way/629487031#map=20/48.5119874/9.0608092&layers=H")

;; TODO: https://wisen.active-group.de in prod
(def base "")

(defn- osm-uri->wisen-osm-uri [osm-uri]
  (str base "/osm/lookup/" (osm-uri->osm-id osm-uri)))

#_(osm-uri->osm-id "https://www.openstreetmap.org/way/629487031")

(defn osm-lookup-request [osm-uri]
  (ajax/GET (osm-uri->wisen-osm-uri osm-uri)
            {:headers {:accept "application/ld+json"}}))

(defn node-osm-uri [node]
  (let [obj (edit-tree/node-object-for-predicate vocabulary/wisen-osm-uri-predicate node)]
    (when (edit-tree/literal-string? obj)
      (edit-tree/literal-string-value obj))))

(defn organization-do-link-osm [organization-node osm-id osm-place-node]
  (-> organization-node
      (edit-tree/node-assoc-replace "http://schema.org/location" osm-place-node)
      (edit-tree/node-assoc-replace vocabulary/wisen-osm-uri-predicate (edit-tree/make-literal-string osm-id))))

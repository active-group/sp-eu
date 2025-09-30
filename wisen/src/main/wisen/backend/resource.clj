(ns wisen.backend.resource
  (:require [wisen.common.prefix :as prefix
             wisen.common.urn :as urn]))

(defn uri-for-resource-id [id]
  (if (urn/urn? id)
    id
    (prefix/resource id)))

(ns wisen.backend.resource
  (:require [wisen.common.prefix :as prefix]))

(defn uri-for-resource-id [id]
  (prefix/resource id))

(defn description-url-for-resource-id [id]
  (str (prefix/prefix) "/api/resource/" id))

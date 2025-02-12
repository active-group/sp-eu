(ns wisen.backend.resource
  (:require [wisen.backend.core :as core]))

(defn uri-for-resource-id [id]
  (str core/*base* "/resource/" id))

(defn description-url-for-resource-id [id]
  (str core/*base* "/api/resource/" id))

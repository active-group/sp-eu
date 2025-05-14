(ns wisen.backend.resource
  (:require [wisen.common.prefix :as prefix]))

(defn uri-for-resource-id [id]
  (prefix/resource id))

;; this must match wisen.common.routes/resource
(defn description-url-for-resource-id [id]
  (str (prefix/prefix) "/resource/" id "/about"))

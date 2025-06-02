(ns wisen.common.wisen-uri
  (:require [clojure.string :as s]
            [wisen.common.prefix :as prefix]))

(defn is-wisen-uri? [uri]
  (and (string? uri)
       (s/starts-with? uri (prefix/resource-prefix))))

(defn wisen-uri-id [uri]
  (subs uri (count (prefix/resource-prefix))))

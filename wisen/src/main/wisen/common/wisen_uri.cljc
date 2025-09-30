(ns wisen.common.wisen-uri
  (:require [clojure.string :as s]
            [wisen.common.prefix :as prefix]))

(defn is-wisen-uri? [uri]
  (and (string? uri)
       (or (s/starts-with? uri (prefix/resource-prefix))
           (s/starts-with? uri "urn:triples:"))))

(defn wisen-uri-id [uri]
  (if (s/starts-with? uri "urn:triples:")
    uri
    (subs uri (count (prefix/resource-prefix)))))

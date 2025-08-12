(ns wisen.backend.importer
  (:require [wisen.backend.triple-store :as triple-store]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.index :as index]))

(defn import-string [s]
  (let [model (jsonld/json-ld-string->model s)]

    (triple-store/setup!)

    (triple-store/decorate-geo! model)

    (triple-store/add-model! model)

    (index/update-search-index!)
    ))

(defn import-file [path]
  (import-string (slurp path)))

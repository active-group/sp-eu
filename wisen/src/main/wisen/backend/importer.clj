(ns wisen.backend.importer
  (:require [wisen.backend.triple-store :as triple-store]
            [wisen.backend.jsonld :as jsonld]))

(defn import-file [path]
  (let [s (slurp path)
        model (jsonld/json-ld-string->model s)]

    (triple-store/setup!)

    (triple-store/decorate-geo! model)

    (triple-store/add-model! model)

    (index/update-search-index!)
    ))

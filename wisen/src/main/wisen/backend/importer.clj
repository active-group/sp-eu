(ns wisen.backend.importer
  (:require [wisen.backend.triple-store :as triple-store]
            [wisen.backend.jsonld :as jsonld]
            [wisen.backend.index :as index]
            [wisen.backend.git :as git]))

(defn import-string [s & [base-commit-id]]
  ;; TODO
  #_(let [base-commit-id (or base-commit-id (git/head! repo-uri))
        model (jsonld/json-ld-string->model s)]

    #_#_#_(triple-store/decorate-geo! model)

    (triple-store/add-model! model)

    (index/update-search-index!)
    ))

(defn import-file [path]
  (import-string (slurp path)))

(ns wisen.backend.skolemizer
  (:require [wisen.backend.skolem :as sk]
            [wisen.backend.jsonld :as jsonld]
            [wisen.common.prefix :as prefix]))

(defn skolemize-file [path]
  (let [s (slurp path)
        model (jsonld/json-ld-string->model s)
        skolemized (sk/skolemize-model model (prefix/resource-prefix))
        skolemized-string (jsonld/model->json-ld-string skolemized)]
    (spit (str path ".skolemized") skolemized-string)))

(ns wisen.backend.skolemizer
  (:require [wisen.backend.skolem :as sk]
            [wisen.backend.jsonld :as jsonld]
            [wisen.common.prefix :as prefix]))

(defn skolemize-string [json-ld-string]
  (let [model (jsonld/json-ld-string->model json-ld-string)
        skolemized (sk/skolemize-model model (prefix/resource-prefix))
        skolemized-string (jsonld/model->json-ld-string skolemized)]
    skolemized-string))

(defn skolemize-file [path]
  (let [s (slurp path)
        skolemized-string (skolemize-string s)]
    (spit (str path ".skolemized") skolemized-string)))

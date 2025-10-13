(ns wisen.backend.access.cache
  (:require
   [active.data.record :as record :refer [def-record]]
   [clojure.core.cache.wrapped :as cache.wrapped]
   [wisen.backend.repository :as repository]
   [wisen.backend.index :as index]))

(def-record result
  [result-model
   result-index-future
   result-is-controlled?
   ])

(defn result-index [cr]
  @(result-index-future cr))

(def-record ^:private cache
  [^:private cache-cache
   ^:private cache-model->index])

(defn new-cache! [model->index]
  (cache

   ;; map [repo-uri commit-id] -> result
   cache-cache
   (cache.wrapped/lru-cache-factory
    {} :threshold 4)

   cache-model->index
   model->index))

(defn- cache-key [repo-uri commit-id]
  [repo-uri commit-id])

(defn get!
  [cache repo-uri commit-id]
  (cache.wrapped/lookup-or-miss (cache-cache cache)
                                (cache-key repo-uri commit-id)
                                (fn [_k]
                                  (let [model (repository/read! repo-uri commit-id)
                                        model->index (cache-model->index cache)]
                                    (result
                                     result-model model
                                     result-index-future (future (model->index model))
                                     result-is-controlled? false)))))


(defn set-model! [cache repo-uri commit-id model controlled?]
  (let [model->index (cache-model->index cache)]
    (cache.wrapped/through-cache (cache-cache cache)
                                 (cache-key repo-uri commit-id)
                                 (constantly
                                  (result
                                   result-model model
                                   result-index-future (future (model->index model))
                                   result-is-controlled? controlled?)))))

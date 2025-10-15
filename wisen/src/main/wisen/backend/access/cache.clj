(ns wisen.backend.access.cache
  (:require
   [active.data.record :as record :refer [def-record is-a?]]
   [clojure.core.cache.wrapped :as cache.wrapped]
   [wisen.backend.repository :as repository]
   [wisen.backend.index :as index]))

(def-record controlled
  [controlled-model
   ^:private controlled-index-future])

(defn controlled? [x]
  (is-a? controlled x))

(defn controlled-index [x]
  @(controlled-index-future x))

(def-record uncontrolled
  [uncontrolled-model])

(defn uncontrolled? [x]
  (is-a? uncontrolled x))

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
                                    #_(uncontrolled uncontrolled-model model)
                                    (controlled
                                     controlled-model model
                                     controlled-index-future (future (model->index model)))))))


(defn set-model! [cache repo-uri commit-id model]
  (let [model->index (cache-model->index cache)]
    (cache.wrapped/through-cache (cache-cache cache)
                                 (cache-key repo-uri commit-id)
                                 (constantly
                                  (controlled
                                   controlled-model model
                                   controlled-index-future (future (model->index model)))))))

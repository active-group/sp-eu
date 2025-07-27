(ns wisen.frontend.search-state
  (:require
   [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
   [active.data.realm :as realm]
   [active.clojure.lens :as lens]
   [wisen.common.query :as query]))

(def-record graph-as-string
  [graph-as-string-value :- realm/string])

(defn make-graph-as-string [s]
  (graph-as-string graph-as-string-value s))

(def-record graph-as-edit-tree
  [graph-as-edit-tree-value])

(defn make-graph-as-edit-tree [etree]
  (graph-as-edit-tree graph-as-edit-tree-value etree))

(def graph
  (realm/union
   graph-as-string
   graph-as-edit-tree))

(def-record search-response
  [search-response-graph :- graph
   search-response-uri-order :- (realm/sequence-of realm/string)
   search-response-total-hits :- realm/integer])

(defn make-search-response [graph uri-order total-hits]
  (search-response
   search-response-graph graph
   search-response-uri-order uri-order
   search-response-total-hits total-hits))

(def-record result-range
  [result-range-start :- (realm/integer-from 0)
   result-range-count :- (realm/integer-from 1)])

(defn result-range-end-exclusive [rr]
  (+ (result-range-start rr)
     (result-range-count rr)))

(defn result-range-end-inclusive [rr]
  (dec (result-range-end-exclusive rr)))

(def page-size 5)

(defn make-result-range
  ([start]
   (make-result-range start page-size))
  ([start cnt]
   (result-range
    result-range-start start
    result-range-count cnt)))

(def initial-result-range
  (make-result-range 0))

(defn serialize-result-range [rr]
  [(result-range-start rr)
   (result-range-count rr)])

(def-record error
  [error-message])

(defn make-error [message]
  (error error-message message))

(def loading ::loading)

(defn loading? [x]
  (= loading x))

(def result
  (realm/union search-response
               error
               (realm/enum loading)))

(def-record search-session
  [search-session-query :- query/query
   ^:private search-session-results :- (realm/map-of
                                        result-range
                                        result)])

(defn create-search-session [query]
  (search-session
   search-session-query
   query
   search-session-results {initial-result-range loading}))

(defn search-session-result-ranges [ss]
  (sort-by result-range-start
           (keys (search-session-results ss))))

(defn search-session-result-for-range [result-range]
  (lens/>>
   search-session-results
   (lens/member result-range)
   (lens/or-else loading)))

(defn search-session-some-loading? [ss]
  (some loading? (vals (search-session-results ss))))

(defn search-session-estimated-total-hits [ss]
  (apply max
         (mapcat (fn [result]
                   (when (is-a? search-response result)
                     [(search-response-total-hits result)]
                     ))
                 (vals (search-session-results ss)))))

(def default-page-size 5)

(defn- search-session-pages* [acc rngs total-hits]
  (let [[acc-rngs next-start-index] acc]
    (cond
      (>= next-start-index total-hits)
      acc-rngs

      (>= (+ next-start-index default-page-size) total-hits)
      (conj
       acc-rngs
       (result-range
        result-range-start next-start-index
        result-range-count (- total-hits next-start-index)))

      :else
      (if-let [rng (first rngs)]
        (if (> (result-range-start rng) next-start-index)
          ;; start of rng is still a bit away
          (let [rng+ (result-range
                      result-range-start next-start-index
                      result-range-count (min
                                          default-page-size
                                          (- (result-range-start rng)
                                             next-start-index)))]
            (search-session-pages*
             [(conj acc-rngs
                    rng+)
              (result-range-end-exclusive rng+)]
             rngs
             total-hits))
          ;; rng is up
          (search-session-pages*
           [(conj acc-rngs
                  rng)
            (result-range-end-exclusive rng)]
           (rest rngs)
           total-hits))
        ;; else no rngs
        (let [rng+ (result-range
                    result-range-start next-start-index
                    result-range-count default-page-size)]
          (search-session-pages*
           [(conj acc-rngs rng+)
            (result-range-end-exclusive rng+)]
           nil
           total-hits))))))

(defn search-session-pages
  "Returns a seq of result-range"
  [ss]
  (let [ranges (search-session-result-ranges ss)]
    (assert (not-empty ranges))
    (when-let [total-hits (search-session-estimated-total-hits ss)]
      (search-session-pages* [[] 0] ranges total-hits))))

(def initial-search-state ::initial)

(def search-state
  (realm/union
   search-session
   (realm/enum initial-search-state)))

(defn search-state-some-loading? [search-state]
  (and (is-a? search-session search-state)
       (search-session-some-loading? search-state)))

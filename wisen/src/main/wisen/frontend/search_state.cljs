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

(defn graph-as-string? [x]
  (is-a? graph-as-string x))

(def-record graph-as-edit-tree
  [graph-as-edit-tree-value])

(defn make-graph-as-edit-tree [etree]
  (graph-as-edit-tree graph-as-edit-tree-value etree))

(defn graph-as-edit-tree? [x]
  (is-a? graph-as-edit-tree x))

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

(defn search-response? [x]
  (is-a? search-response x))

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

;; --- Search session results

(def search-session-results-type
  (realm/map-of
   result-range
   result))

(def initial-search-session-results
  {initial-result-range loading})

(defn search-session-results-ranges [ssr]
  (sort-by result-range-start
           (keys ssr)))

(defn search-session-results-result-for-range [result-range]
  (lens/>>
   (lens/member result-range)
   (lens/or-else loading)))

(defn search-session-results-estimated-total-hits [ssr]
  (apply max
         (mapcat (fn [result]
                   (when (is-a? search-response result)
                     [(search-response-total-hits result)]
                     ))
                 (vals ssr))))

(defn search-session-results-some-loading? [ssr]
  (some loading? (vals ssr)))

(def default-page-size 5)

(defn- search-session-results-pages* [acc rngs total-hits]
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
            (search-session-results-pages*
             [(conj acc-rngs
                    rng+)
              (result-range-end-exclusive rng+)]
             rngs
             total-hits))
          ;; rng is up
          (search-session-results-pages*
           [(conj acc-rngs
                  rng)
            (result-range-end-exclusive rng)]
           (rest rngs)
           total-hits))
        ;; else no rngs
        (let [rng+ (result-range
                    result-range-start next-start-index
                    result-range-count default-page-size)]
          (search-session-results-pages*
           [(conj acc-rngs rng+)
            (result-range-end-exclusive rng+)]
           nil
           total-hits))))))

(defn search-session-results-pages
  "Returns a seq of result-range"
  [ssr]
  (assert (not-empty ssr))
  (when-let [total-hits (search-session-results-estimated-total-hits ssr)]
    (search-session-results-pages* [[] 0] (keys ssr) total-hits)))

;; --- Search session

(def-record search-session
  [search-session-query :- query/query
   search-session-results :- search-session-results-type])

(defn create-search-session [query]
  (search-session
   search-session-query query
   search-session-results initial-search-session-results))

(defn search-session? [x]
  (is-a? search-session x))

(defn search-session-some-loading? [ss]
  (search-session-results-some-loading?
   (search-session-results ss)))

;; --- Search state

(def initial-search-state ::initial)

(def search-state
  (realm/union
   search-session
   (realm/enum initial-search-state)))

(defn search-state-some-loading? [search-state]
  (and (is-a? search-session search-state)
       (search-session-some-loading? search-state)))

(ns wisen.frontend.search-state
  (:require
   [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
   [active.data.realm :as realm]
   [active.clojure.lens :as lens]
   [wisen.common.query :as query]
   [wisen.frontend.edit-tree :as edit-tree]
   [wisen.frontend.tree :as tree]))

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

(declare tree-geo-positions)

(letfn [(unwrap [obj]
          (cond
            (tree/literal-decimal? obj)
            (parse-double (tree/literal-decimal-value obj))

            (tree/literal-string? obj)
            (parse-double (tree/literal-string-value obj))
            ))]
  (defn- node-geo-position [node]
    (let [lat (or ((tree/node-object-for-predicate "http://schema.org/latitude") node)
                  ((tree/node-object-for-predicate "http://www.w3.org/2003/01/geo/wgs84_pos#lat") node))
          long (or ((tree/node-object-for-predicate "http://schema.org/longitude") node)
                   ((tree/node-object-for-predicate "http://www.w3.org/2003/01/geo/wgs84_pos#long") node))]
      (when-let [lt (unwrap lat)]
        (when-let [lng (unwrap long)]
          (let [node-uri (tree/node-uri node)]
            [[lt lng] node-uri]))))))

(defn- node-geo-positions [node]
  (let [poss (mapcat (fn [prop]
                       (tree-geo-positions
                        (tree/property-object prop)))
                     (tree/node-properties node))]
    (if-let [pos (node-geo-position node)]
      (conj poss pos)
      poss)))

(defn- tree-geo-positions [tree]
  (cond
    (tree/ref? tree)
    []

    (tree/literal-string? tree)
    []

    (tree/literal-decimal? tree)
    []

    (tree/literal-boolean? tree)
    []

    (tree/literal-time? tree)
    []

    (tree/literal-date? tree)
    []

    (tree/many? tree)
    (mapcat tree-geo-positions (tree/many-trees tree))

    (tree/exists? tree)
    []

    (tree/node? tree)
    (node-geo-positions tree)))

(defn graph-geo-positions [g]
  (cond
    (graph-as-string? g)
    []

    (graph-as-edit-tree? g)
    (tree-geo-positions
     (edit-tree/edit-tree-result-tree
      (graph-as-edit-tree-value g)))))

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

(defn search-response-geo-positions [resp]
  (graph-geo-positions
   (search-response-graph resp)))

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

(defn error? [x]
  (is-a? error x))

(def loading ::loading)

(defn loading? [x]
  (= loading x))

(def result
  (realm/union search-response
               error
               (realm/enum loading)))

(defn result-geo-positions [result]
  (cond
    (error? result)
    []

    (loading? result)
    []

    (search-response? result)
    (search-response-geo-positions result)))

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
   search-session-results :- search-session-results-type
   search-session-selected-result-range :- result-range])

(defn create-search-session [query]
  (search-session
   search-session-query query
   search-session-results initial-search-session-results
   search-session-selected-result-range initial-result-range))

(defn search-session? [x]
  (is-a? search-session x))

(defn search-session-selected-result
  ([ss]
   (let [selected (search-session-selected-result-range ss)]
     (lens/yank
      ss
      (lens/>>
       search-session-results
       (search-session-results-result-for-range selected)))))
  ([ss results]
   (let [selected (search-session-selected-result-range ss)]
     (lens/shove
      ss
      (lens/>>
       search-session-results
       (search-session-results-result-for-range selected))
      results))))

(defn search-session-some-loading? [ss]
  (search-session-results-some-loading?
   (search-session-results ss)))

(defn search-session-geo-positions [ss]
  (result-geo-positions
   (search-session-selected-result ss)))

;; --- Search state

(def initial-search-state ::initial)

(def search-state
  (realm/union
   search-session
   (realm/enum initial-search-state)))

(defn search-state-some-loading? [search-state]
  (and (is-a? search-session search-state)
       (search-session-some-loading? search-state)))

(defn search-state-geo-positions [ss]
  (if (search-session? ss)
    (search-session-geo-positions ss)
    []))

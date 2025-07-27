(ns wisen.frontend.search-state-test
  (:require [wisen.frontend.search-state :as ss]
            [wisen.common.query :as q]
            [active.clojure.lens :as lens]
            [cljs.test :refer-macros [deftest is testing async]]))

(def sess (ss/create-search-session q/initial-query))

(deftest search-session-estimated-total-hits-t
  (is (=
       nil
       (ss/search-session-estimated-total-hits
          sess))))

(deftest search-session-pages-t
  (is (= nil
         (ss/search-session-pages
          (ss/create-search-session q/initial-query))))

  (is (= [(ss/make-result-range 0 5)
          (ss/make-result-range 5 3)]
         (ss/search-session-pages
          (-> sess
              (lens/shove (ss/search-session-result-for-range (ss/make-result-range 0 5))
                          (ss/make-search-response
                           (ss/make-graph-as-string "graph")
                           ["foo" "bar"]
                           8))))))

  (is (= [(ss/make-result-range 0 5)
          (ss/make-result-range 5 4)]
         (ss/search-session-pages
          (-> sess
              (lens/shove (ss/search-session-result-for-range (ss/make-result-range 0 5))
                          (ss/make-search-response
                           (ss/make-graph-as-string "graph")
                           ["foo" "bar"]
                           8))
              (lens/shove (ss/search-session-result-for-range (ss/make-result-range 5 4))
                          (ss/make-search-response
                           (ss/make-graph-as-string "graph")
                           ["foo" "bar"]
                           9))))))

  (is (= [(ss/make-result-range 0 5)
          (ss/make-result-range 5 3)
          (ss/make-result-range 8 5)
          (ss/make-result-range 13 2)]
         (ss/search-session-pages
          (-> sess
              (lens/shove (ss/search-session-result-for-range (ss/make-result-range 0 5))
                          (ss/make-search-response
                           (ss/make-graph-as-string "graph")
                           ["foo" "bar"]
                           15))
              (lens/shove (ss/search-session-result-for-range (ss/make-result-range 8 5))
                          (ss/make-search-response
                           (ss/make-graph-as-string "graph")
                           ["foo" "bar"]
                           15)))))))

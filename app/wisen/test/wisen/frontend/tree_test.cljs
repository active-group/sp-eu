(ns wisen.frontend.tree-test
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [cljs.test :refer-macros [deftest is testing async]]))

(def jsonld
  (js/JSON.stringify (clj->js
                      {"@id" "http://example.org/alice"
                       "http://example.org/name" ["Alice" "Alicia"]})))

(def g (rdf/json-ld-string->graph-promise jsonld))

(deftest graph->trees-test
  (async done
    (.then g
           (fn [g]
             (testing "graph->trees"
               (is (= [(tree/make-node "http://example.org/alice"
                                       [(tree/make-property "http://example.org/name" (tree/make-literal-string "Alice"))
                                        (tree/make-property "http://example.org/name" (tree/make-literal-string "Alicia"))])]
                      (tree/graph->trees g))))

             (done)))))

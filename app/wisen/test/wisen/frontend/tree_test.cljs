(ns wisen.frontend.tree-test
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [cljs.test :refer-macros [deftest is testing async]]))

(defn- with-graph [m k]
  (async done
    (.then
     (rdf/json-ld-string->graph-promise
      (js/JSON.stringify (clj->js m)))
     (fn [g]
       (k g done)))))

(deftest graph->tree-test
  (with-graph
    {"@id" "http://example.org/alice"
     "http://example.org/name" ["Alice"]}
    (fn [g done]
      (is (= (tree/make-node "http://example.org/alice"
                             [(tree/make-property "http://example.org/name" (tree/make-literal-string "Alice"))])
             (tree/graph->tree g)))
      (done))))

(deftest graph-tree-test-2
  (with-graph
    {"@id" "http://example.org/alice"
     "http://example.org/name" ["Alice" "Alicia"]}
    (fn [g done]
      (is (= (tree/make-node "http://example.org/alice"
                             [(tree/make-property "http://example.org/name" (tree/make-literal-string "Alice"))
                              (tree/make-property "http://example.org/name" (tree/make-literal-string "Alicia"))])
             (tree/graph->tree g)))
      (done))))

(deftest get-produce-existential-text
  (is (= (tree/get-produce-existential {} "foo")
         [{"foo" 0} 0]))

  (is (= (tree/get-produce-existential {"foo" 0} "foo")
         [{"foo" 0} 0]))

  (is (= (tree/get-produce-existential {"foo" 0} "bar")
         [{"foo" 0 "bar" 1} 1]))

  (is (= (tree/get-produce-existential {"foo" 0 "bar" 1} "boo")
         [{"foo" 0 "bar" 1 "boo" 2} 2])))

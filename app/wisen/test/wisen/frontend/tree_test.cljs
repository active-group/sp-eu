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

(deftest graph-tree-test-3
  (with-graph
    {"http://schema.org/name" "Klostermühle"
     "http://schema.org/location" {"http://schema.org/geo" {"http://schema.org/longitude" 9.225}}}
    (fn [g done]
      (let [tree (tree/graph->tree g)]
        (is (tree/exists? tree))
        (let [tree* ((tree/exists-k tree) 0)]
          (is (tree/exists? tree*))
          (let [tree** ((tree/exists-k tree*) 1)]
            (is (tree/exists? tree**))
            (let [tree*** ((tree/exists-k tree**) 2)]
              (is (tree/node? tree***))
              (is (= tree***
                     (tree/make-node
                      0
                      [(tree/make-property "http://schema.org/location"
                                           (tree/make-node
                                            1
                                            [(tree/make-property "http://schema.org/geo"
                                                                 (tree/make-node
                                                                  2
                                                                  [(tree/make-property "http://schema.org/longitude"
                                                                                       (tree/make-literal-decimal "9.225"))]))]))
                       (tree/make-property "http://schema.org/name"
                                           (tree/make-literal-string "Klostermühle"))])))

              ))))
      (done))))

(deftest get-produce-existential-text
  (is (= (tree/get-produce-existential {"foo" 0} "foo")
         [{"foo" 0} 0])))

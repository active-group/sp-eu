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
        (let [ex (tree/exists-existential tree)
              tree* (tree/exists-tree tree)]
          (is (tree/exists? tree*))
          (let [ex* (tree/exists-existential tree*)
                tree** (tree/exists-tree tree*)]
            (is (tree/exists? tree**))
            (let [ex** (tree/exists-existential tree**)
                  tree*** (tree/exists-tree tree**)]
              (is (tree/node? tree***))
              (is (= tree***
                     (tree/make-node
                      ex**
                      [(tree/make-property "http://schema.org/location"
                                           (tree/make-node
                                            ex*
                                            [(tree/make-property "http://schema.org/geo"
                                                                 (tree/make-node
                                                                  ex
                                                                  [(tree/make-property "http://schema.org/longitude"
                                                                                       (tree/make-literal-decimal "9.225"))]))]))
                       (tree/make-property "http://schema.org/name"
                                           (tree/make-literal-string "Klostermühle"))])))

              ))))
      (done))))

(deftest graph-tree-test-equality
  (with-graph
    {"http://schema.org/name" "Klostermühle"
     "http://schema.org/location" {"http://schema.org/geo" {"http://schema.org/longitude" 9.225}}}
    (fn [g done]
      (is (= (tree/graph->tree g)
             (tree/graph->tree g)))
      (done))))


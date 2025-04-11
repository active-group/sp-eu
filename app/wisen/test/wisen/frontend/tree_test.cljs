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

(deftest node-properties-derived-uri
  (let [props-1 [(tree/make-property "http://schema.org/longitude"
                                     (tree/make-literal-decimal "1.0"))
                 (tree/make-property "http://schema.org/latitude"
                                     (tree/make-literal-decimal "1.0"))
                 (tree/make-property "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                                     (tree/make-node "http://schema.org/GeoCoordinates"
                                                     []))]

        props-2 [(tree/make-property "http://schema.org/longitude"
                                     (tree/make-literal-decimal "20.0"))
                 (tree/make-property "http://schema.org/latitude"
                                     (tree/make-literal-decimal "1.0"))
                 (tree/make-property "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                                     (tree/make-node "http://schema.org/GeoCoordinates"
                                                     []))]]

    (is (= (tree/node-properties-derived-uri
            (tree/make-node "http://example.org/foobar" props-1))
           props-1))

    ;; URI is made the same
    (is (= (tree/node-properties-derived-uri (tree/make-node "http://example.org/foobar" props-1)
                                             props-2)
           (tree/node-properties-derived-uri (tree/make-node "http://example.org/bla" props-1)
                                             props-2)))))

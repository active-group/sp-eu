(ns wisen.frontend.rdf-test
  (:require [wisen.frontend.rdf :as rdf]
            [cljs.test :refer-macros [deftest is testing async]]))

(def jsonld
  (js/JSON.stringify (clj->js
                      {"@context" {"foaf" "http://xmlns.com/foaf/0.1/"
                                   "rdf" "http://www.w3.org/1999/02/22-rdf-syntax-ns#"}
                       "@id" "http://example.org/alice"
                       "@type" "http://schema.org/Person"
                       "foaf:name" "Alice"
                       "foaf:knows" {"@id" "http://example.org/bob"
                                     "foaf:name" "Bob"}})))

(def g (rdf/json-ld-string->graph-promise jsonld))

(deftest types-test

  (let [uri "http://example.org/foo"
        s (rdf/make-symbol uri)]
    (is (= true (rdf/symbol? s)))
    (is (not (rdf/symbol? "bla")))
    (is (= uri (rdf/symbol-uri s))))

  (let [bn (rdf/make-blank-node)]
    (is (= true (rdf/blank-node? bn))))

  (let [s "foo"
        lit (rdf/make-literal-string s)]
    (is (rdf/literal-string? lit))
    (is (not (rdf/literal-string? "bla")))
    (is (= s (rdf/literal-string-value lit))))

  (let [node (rdf/make-symbol "http://example.org/foo")
        nodes (rdf/make-collection [node])]
    (is (rdf/collection? nodes))
    (is (= [node] (rdf/collection-elements nodes)))))

(deftest graph-test
  (async done
    (.then g
           (fn [g]
             (testing "subjects"
               (is (= '("http://example.org/alice" "http://example.org/bob")
                      (map rdf/symbol-uri (rdf/subjects g)))))

             (testing "subject-properties"
               (let [props (rdf/subject-properties g (rdf/make-symbol "http://example.org/alice"))]
                 (is (= #{"http://www.w3.org/1999/02/22-rdf-syntax-ns#type" "http://xmlns.com/foaf/0.1/knows" "http://xmlns.com/foaf/0.1/name"}
                        (set
                         (map (comp rdf/symbol-uri rdf/property-predicate)
                              props))))
                 (is (= #{"http://schema.org/Person" "http://example.org/bob" "Alice"}
                        (set
                         (map (comp rdf/node-to-string rdf/property-object)
                              props))))))

             (done)))))

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

(deftest equality-test
  (is (= (rdf/make-symbol "http://example.org/name")
         (rdf/make-symbol "http://example.org/name"))))

(deftest graph-test
  (async done
    (.then g
           (fn [g]
             (testing "subjects"
               (is (= '("http://example.org/alice" "http://example.org/bob")
                      (map rdf/symbol-uri (rdf/subjects g)))))

             (testing "roots"
               (is (= '("http://example.org/alice")
                      (map rdf/symbol-uri (rdf/roots g)))))

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

(deftest graph-test-2
  (async done
    (.then (rdf/json-ld-string->graph-promise
            (js/JSON.stringify (clj->js
                                {"@id" "http://example.org/alice"
                                 "http://example.org/name" ["Alice" "Alicia"]})))

           (fn [g]
             (testing "subject-properties multiple"

               (is (= #{(rdf/make-symbol "http://example.org/name")}
                      (rdf/subject-predicates g (rdf/make-symbol "http://example.org/alice"))))

               (is (= #{(rdf/property
                         rdf/property-predicate (rdf/make-symbol "http://example.org/name")
                         rdf/property-object (rdf/make-literal-string "Alice"))
                        (rdf/property
                         rdf/property-predicate (rdf/make-symbol "http://example.org/name")
                         rdf/property-object (rdf/make-literal-string "Alicia"))}
                      (set (rdf/subject-properties g (rdf/make-symbol "http://example.org/alice"))))))

             (done)))))

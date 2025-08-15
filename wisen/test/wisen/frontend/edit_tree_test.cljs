(ns wisen.frontend.edit-tree-test
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.edit-tree :as et]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.change :as change]
            [active.data.realm.validation :as v]
            [cljs.test :refer-macros [use-fixtures deftest is testing async]]))

(use-fixtures :each (fn [f] (v/checking (f))))

(deftest edit-tree-changeset-test

  (testing "all same"
    (is (= []
           (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://schema.org/name"
                                      [(et/mark-same (et/make-literal-string "Foobar"))]
                                      "http://schema.org/description"
                                      [(et/mark-same (et/make-literal-string "Descr"))]})))))

  (testing "literal added"
    (is (= [(change/make-add
             (change/make-statement "http://example.org/a"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))]
           (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://schema.org/name"
                                      [(et/mark-added (et/make-literal-string "Foobar"))]})))))

  (testing "literal removed"
    (is (= [(change/make-delete
             (change/make-statement "http://example.org/a"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))]
           (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://schema.org/name"
                                      [(et/make-deleted (et/make-literal-string "Foobar"))]})))))

  (testing "literal changed"
    (is (= (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://schema.org/name"
                                      [(et/make-maybe-changed
                                        (et/make-literal-string "Foobar")
                                        (et/make-literal-string "Barfoo"))]}))
           [(change/make-delete
             (change/make-statement "http://example.org/a"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))
            (change/make-add
             (change/make-statement "http://example.org/a"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Barfoo")))])))

  (testing "nested added"
    (is (= [(change/make-add
             (change/make-statement "http://example.org/b"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))]
           (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://example.org/foo"
                                      [(et/make-maybe-changed
                                        ;; before
                                        (et/edit-node
                                         et/edit-node-uri "http://example.org/b"
                                         et/edit-node-properties {})
                                        ;; after
                                        (et/edit-node
                                         et/edit-node-uri "http://example.org/b"
                                         et/edit-node-properties {"http://schema.org/name"
                                                                  [(et/mark-added (et/make-literal-string "Foobar"))]}))]})))))

  (testing "nested deleted"
    (is (= [(change/make-delete
             (change/make-statement "http://example.org/b"
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))]
           (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "http://example.org/a"
             et/edit-node-properties {"http://example.org/foo"
                                      [(et/make-maybe-changed
                                        ;; before
                                        (et/edit-node
                                         et/edit-node-uri "http://example.org/b"
                                         et/edit-node-properties {"http://schema.org/name"
                                                                  [(et/mark-same (et/make-literal-string "Foobar"))]})
                                        ;; after
                                        (et/edit-node
                                         et/edit-node-uri "http://example.org/b"
                                         et/edit-node-properties {"http://schema.org/name"
                                                                  [(et/make-deleted (et/make-literal-string "Foobar"))]}))]})))))

  (testing "delete inside delete"
    (is (= (set [(change/make-delete
                  (change/make-statement "http://example.org/a"
                                         "http://example.org/foo"
                                         "http://example.org/b"))
                 (change/make-delete
                  (change/make-statement "http://example.org/b"
                                         "http://schema.org/name"
                                         (tree/make-literal-string "Foobar")))])
           (set (et/edit-tree-changeset
                 (et/edit-node
                  et/edit-node-uri "http://example.org/a"
                  et/edit-node-properties {"http://example.org/foo"
                                           [(et/make-deleted
                                             (et/edit-node
                                              et/edit-node-uri "http://example.org/b"
                                              et/edit-node-properties {"http://schema.org/name"
                                                                       [(et/make-deleted (et/make-literal-string "Foobar"))]}))]}))))))
  (is (= (set [(change/make-add
                (change/make-statement "http://example.org/a"
                                       "http://example.org/foo"
                                       "http://example.org/b"))
               (change/make-add
                (change/make-statement "http://example.org/b"
                                       "http://schema.org/name"
                                       (tree/make-literal-string "Foobar")))])
         (set (et/edit-tree-changeset
               (et/edit-node
                et/edit-node-uri "http://example.org/a"
                et/edit-node-properties {"http://example.org/foo"
                                         [(et/mark-added
                                           (et/edit-node
                                            et/edit-node-uri "http://example.org/b"
                                            et/edit-node-properties {"http://schema.org/name"
                                                                     [(et/mark-added (et/make-literal-string "Foobar"))]}))]})))))

  (testing "add inside add with blank nodes"
    (is (= (et/edit-tree-changeset
            (et/make-exists
             0
             (et/edit-node
              et/edit-node-uri "A"
              et/edit-node-properties {"http://example.org/foo"
                                       [(et/mark-added
                                         (et/edit-node
                                          et/edit-node-uri 0
                                          et/edit-node-properties {"http://schema.org/name"
                                                                   [(et/mark-added (et/make-literal-string "Foobar"))]}))]})))
           [(change/make-with-blank-node
             0
             [(change/make-add
               (change/make-statement "A"
                                      "http://example.org/foo"
                                      0))
              (change/make-add
               (change/make-statement 0
                                      "http://schema.org/name"
                                      (tree/make-literal-string "Foobar")))])])))

  (testing "add with blank node inside add"
    (is (= (et/edit-tree-changeset
            (et/edit-node
             et/edit-node-uri "A"
             et/edit-node-properties {"http://example.org/foo"
                                      [(et/mark-added
                                        (et/make-exists
                                         0
                                         (et/edit-node
                                          et/edit-node-uri 0
                                          et/edit-node-properties {"http://schema.org/name"
                                                                   [(et/mark-added (et/make-literal-string "Foobar"))]})))]}))
           [(change/make-with-blank-node
             0
             [(change/make-add
               (change/make-statement "A"
                                      "http://example.org/foo"
                                      0))
              (change/make-add
               (change/make-statement 0
                                      "http://schema.org/name"
                                      (tree/make-literal-string "Foobar")))])]))))

(deftest insert-properties-test
  (is (= (et/insert-properties (et/edit-node et/edit-node-uri "uri"
                                             et/edit-node-properties {})
                               [])
         (et/edit-node et/edit-node-uri "uri"
                                             et/edit-node-properties {})))
  (let [metree (et/mark-added (et/make-literal-string "added string"))
        di-metree (et/mark-added (et/make-literal-string "another string"))
        tri-metree (et/make-maybe-changed (et/make-literal-string "a") (et/make-literal-string "b"))]
    (is (= (et/insert-properties (et/edit-node et/edit-node-uri "uri"
                                               et/edit-node-properties {"pred" [metree]})
                                 [(tree/make-property "pred" (tree/make-literal-string "another string"))])
           (et/edit-node et/edit-node-uri "uri"
                         et/edit-node-properties {"pred" [metree di-metree]})))

    (is (= (et/insert-properties (et/edit-node et/edit-node-uri "uri"
                                               et/edit-node-properties {"pred" [metree]})
                                 [(tree/make-property "pred" (tree/make-literal-string "added string"))])
           (et/edit-node et/edit-node-uri "uri"
                         et/edit-node-properties {"pred" [metree]})))

    (is (= (et/insert-properties (et/edit-node et/edit-node-uri "uri"
                                               et/edit-node-properties {"pred" [metree tri-metree]})
                                 [(tree/make-property "pred" (tree/make-literal-string "b"))])
           (et/edit-node et/edit-node-uri "uri"
                         et/edit-node-properties {"pred" [metree tri-metree]})))))

(deftest some-edit-tree-uri-test

  (testing "simple"
    (let [etree (et/many
                 et/many-edit-trees
                 [(et/edit-node
                   et/edit-node-uri "sub"
                   et/edit-node-properties {"pred" [(et/mark-added
                                                     (et/make-literal-string "added string"))
                                                    (et/make-maybe-changed
                                                     (et/make-ref "deleted-ref")
                                                     (et/make-ref "inserted-ref"))
                                                    (et/mark-added
                                                     (et/edit-node
                                                      et/edit-node-uri "obj"
                                                      et/edit-node-properties {}))
                                                    (et/mark-added
                                                     (et/make-exists
                                                      0
                                                      (et/edit-node
                                                       et/edit-node-uri 0
                                                       et/edit-node-properties
                                                       {"prid"
                                                        [(et/mark-added
                                                          (et/edit-node
                                                           et/edit-node-uri "foo"
                                                           et/edit-node-properties {}))]})))]})])]
      (is (= "sub"
             (et/some-edit-tree-uri #{"sub"} etree)))
      (is (= "obj"
             (et/some-edit-tree-uri #{"obj"} etree)))
      (is (= false
             (et/some-edit-tree-uri #{"bla"} etree)))
      (is (= "inserted-ref"
             (et/some-edit-tree-uri #{"inserted-ref"} etree)))
      (is (= false
             (et/some-edit-tree-uri #{"deleted-ref"} etree)))
      (is (= "foo"
             (et/some-edit-tree-uri #{"foo"} etree))))))

(deftest set-reference-test

  (testing "simple"
    (let [etree (et/edit-node
                 et/edit-node-uri "sub"
                 et/edit-node-properties {"pred" [(et/mark-added
                                                   (et/make-literal-string "added string"))
                                                  (et/mark-added
                                                   (et/edit-node
                                                    et/edit-node-uri "obj"
                                                    et/edit-node-properties {}))]})]
      (is (= (et/edit-node
              et/edit-node-uri "sub"
              et/edit-node-properties {"pred" [(et/mark-added
                                                (et/make-literal-string "added string"))
                                               (et/mark-added
                                                (et/edit-node
                                                 et/edit-node-uri "obj-replaced"
                                                 et/edit-node-properties {}))]})
             (et/set-reference etree
                               "sub"
                               "pred"
                               "obj"
                               "obj-replaced")))))

  (testing "many"
    (let [etree (et/many
                 et/many-edit-trees
                 [(et/edit-node
                   et/edit-node-uri "sub"
                   et/edit-node-properties {"pred" [(et/mark-added
                                                     (et/edit-node
                                                      et/edit-node-uri "obj"
                                                      et/edit-node-properties {}))]})])]
      (is (= (et/many
              et/many-edit-trees
              [(et/edit-node
                et/edit-node-uri "sub"
                et/edit-node-properties {"pred" [(et/mark-added
                                                  (et/edit-node
                                                   et/edit-node-uri "obj-replaced"
                                                   et/edit-node-properties {}))]})])
             (et/set-reference etree
                               "sub"
                               "pred"
                               "obj"
                               "obj-replaced")))))

  (testing "passing exists"
    (let [etree (et/make-exists
                 0
                 (et/edit-node
                  et/edit-node-uri 0
                  et/edit-node-properties
                  {"prid"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri "foo"
                      et/edit-node-properties
                      {"prod"
                       [(et/mark-added
                         (et/edit-node
                          et/edit-node-uri "bar"
                          et/edit-node-properties {}))]}))]}))

          result (et/set-reference etree
                                   "foo"
                                   "prod"
                                   "bar"
                                   "bar-replaced")]

      (is (et/exists? result))
      (is (= (et/edit-node
              et/edit-node-uri 0
              et/edit-node-properties
              {"prid"
               [(et/mark-added
                 (et/edit-node
                  et/edit-node-uri "foo"
                  et/edit-node-properties
                  {"prod"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri "bar-replaced"
                      et/edit-node-properties {}))]}))]})
             (et/exists-edit-tree result)
             ))))

  (testing "can set-reference with existential subject"
    (let [etree (et/make-exists
                 0
                 (et/edit-node
                  et/edit-node-uri 0
                  et/edit-node-properties
                  {"prid"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri "foo"
                      et/edit-node-properties
                      {"prod"
                       [(et/mark-added
                         (et/edit-node
                          et/edit-node-uri "bar"
                          et/edit-node-properties {}))]}))]}))

          result (et/set-reference etree
                                   0
                                   "prid"
                                   "foo"
                                   "foo-replaced")]

      (is (et/exists? result))
      (is (= (et/edit-node
              et/edit-node-uri 0
              et/edit-node-properties
              {"prid"
               [(et/mark-added
                 (et/edit-node
                  et/edit-node-uri "foo-replaced"
                  et/edit-node-properties
                  {}))]})
             (et/exists-edit-tree result)
             ))))

  (testing "can set-reference with existential before-object"
    (let [etree (et/make-exists
                 0
                 (et/edit-node
                  et/edit-node-uri "sub"
                  et/edit-node-properties
                  {"pred"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri 0
                      et/edit-node-properties
                      {}))]}))

          result (et/set-reference etree
                                   "sub"
                                   "pred"
                                   0
                                   "obj")]

      (is (= (et/edit-node
              et/edit-node-uri "sub"
              et/edit-node-properties
              {"pred"
               [(et/mark-added
                 (et/edit-node
                  et/edit-node-uri "obj"
                  et/edit-node-properties
                  {}))]})
             result
             ))))

  (testing "can set-reference with existential after-object"
    (let [etree (et/make-exists
                 0
                 (et/edit-node
                  et/edit-node-uri "sub"
                  et/edit-node-properties
                  {"pred1"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri 0
                      et/edit-node-properties
                      {}))]
                   "pred2"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri "foo"
                      et/edit-node-properties
                      {}))]}))

          result (et/set-reference etree
                                   "sub"
                                   "pred2"
                                   "foo"
                                   0)]

      (is (= (et/make-exists
              0
              (et/edit-node
               et/edit-node-uri "sub"
               et/edit-node-properties
               {"pred1"
                [(et/mark-added
                  (et/edit-node
                   et/edit-node-uri 0
                   et/edit-node-properties
                   {}))]
                "pred2"
                [(et/mark-added
                  (et/make-ref 0))]}))
             result
             ))))

  (testing "form a loop"
    (let [etree (et/edit-node
                 et/edit-node-uri "sub"
                 et/edit-node-properties
                 {"pred"
                  [(et/mark-added
                    (et/edit-node
                     et/edit-node-uri "obj"
                     et/edit-node-properties
                     {}))]})

          result (et/set-reference etree
                                   "sub"
                                   "pred"
                                   "obj"
                                   "sub")]

      (is (= (et/edit-node
              et/edit-node-uri "sub"
              et/edit-node-properties
              {"pred"
               [(et/mark-added
                 (et/make-ref "sub"))]})
             result
             ))))

  (testing "form a loop, exists"
    (let [etree (et/exists
                 et/exists-existential 0
                 et/exists-edit-tree
                 (et/edit-node
                  et/edit-node-uri 0
                  et/edit-node-properties
                  {"pred"
                   [(et/mark-added
                     (et/edit-node
                      et/edit-node-uri "obj"
                      et/edit-node-properties
                      {}))]}))

          result (et/set-reference etree
                                   0
                                   "pred"
                                   "obj"
                                   0)]

      (is (= (et/exists
              et/exists-existential 0
              et/exists-edit-tree
              (et/edit-node
               et/edit-node-uri 0
               et/edit-node-properties
               {"pred"
                [(et/mark-added
                  (et/make-ref 0))]}))
             result
             ))))

  (testing "exists -> [exists/ref]"
    (let [etree (et/exists
                 et/exists-existential 0
                 et/exists-edit-tree
                 (et/edit-node
                  et/edit-node-uri 0
                  et/edit-node-properties
                  {"location"
                   [(et/mark-added
                     (et/exists
                      et/exists-existential 1
                      et/exists-edit-tree
                      (et/edit-node
                       et/edit-node-uri 1
                       et/edit-node-properties
                       {"address" [(et/mark-added
                                    (et/exists
                                     et/exists-existential 2
                                     et/exists-edit-tree
                                     (et/edit-node
                                      et/edit-node-uri 2
                                      et/edit-node-properties
                                      {"street" [(et/mark-added (et/edit-node
                                                                 et/edit-node-uri "some-street"
                                                                 et/edit-node-properties
                                                                 {}))]})))]})))]
                   "address" [(et/mark-added
                               (et/edit-node
                                et/edit-node-uri "some-address"
                                et/edit-node-properties {}))]}))

          result (et/set-reference etree
                                   1
                                   "address"
                                   2
                                   "some-address")]


      (is (= (et/exists
              et/exists-existential 0
              et/exists-edit-tree
              (et/edit-node
               et/edit-node-uri 0
               et/edit-node-properties
               {"location"
                [(et/mark-added
                  (et/exists
                   et/exists-existential 1
                   et/exists-edit-tree
                   (et/edit-node
                    et/edit-node-uri 1
                    et/edit-node-properties
                    {"address" [(et/mark-added
                                 (et/make-ref "some-address"))]})))]
                "address" [(et/mark-added
                            (et/edit-node
                             et/edit-node-uri "some-address"
                             et/edit-node-properties {}))]}))
             result
             )))))

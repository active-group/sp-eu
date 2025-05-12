(ns wisen.frontend.edit-tree-test
  (:require [wisen.frontend.rdf :as rdf]
            [wisen.frontend.edit-tree :as et]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.change :as change]
            [active.data.realm.validation :as v]
            [cljs.test :refer-macros [use-fixtures deftest is testing async]]))

(use-fixtures :each (fn [f] (v/checking (f))))

(deftest edit-tree-changeset-test

  ;; all same
  (is (= []
         (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "http://example.org/a"
           et/edit-node-properties {"http://schema.org/name"
                                    [(et/mark-same (et/make-literal-string "Foobar"))]
                                    "http://schema.org/description"
                                    [(et/mark-same (et/make-literal-string "Descr"))]}))))

  ;; literal added
  (is (= [(change/make-add
           (change/make-statement "http://example.org/a"
                                  "http://schema.org/name"
                                  (tree/make-literal-string "Foobar")))]
         (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "http://example.org/a"
           et/edit-node-properties {"http://schema.org/name"
                                    [(et/mark-added (et/make-literal-string "Foobar"))]}))))

  ;; literal removed
  (is (= [(change/make-delete
           (change/make-statement "http://example.org/a"
                                  "http://schema.org/name"
                                  (tree/make-literal-string "Foobar")))]
         (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "http://example.org/a"
           et/edit-node-properties {"http://schema.org/name"
                                    [(et/make-deleted (et/make-literal-string "Foobar"))]}))))

  ;; literal changed
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
                                  (tree/make-literal-string "Barfoo")))]))

  ;; nested added
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
                                                                [(et/mark-added (et/make-literal-string "Foobar"))]}))]}))))

  ;; nested delete
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
                                                                [(et/make-deleted (et/make-literal-string "Foobar"))]}))]}))))

  ;; delete inside delete
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
                                                                     [(et/make-deleted (et/make-literal-string "Foobar"))]}))]})))))
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

  ;; add inside add with blank nodes
  (is (= (et/edit-tree-changeset
          (et/exists
           et/exists-k
           (fn [ex]
             (et/edit-node
              et/edit-node-uri "A"
              et/edit-node-properties {"http://example.org/foo"
                                       [(et/mark-added
                                         (et/edit-node
                                          et/edit-node-uri ex
                                          et/edit-node-properties {"http://schema.org/name"
                                                                   [(et/mark-added (et/make-literal-string "Foobar"))]}))]}))))
         [(change/make-with-blank-node
           0
           [(change/make-add
             (change/make-statement "A"
                                    "http://example.org/foo"
                                    0))
            (change/make-add
             (change/make-statement 0
                                    "http://schema.org/name"
                                    (tree/make-literal-string "Foobar")))])]))

  ;; add with blank node inside add
  (is (= (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "A"
           et/edit-node-properties {"http://example.org/foo"
                                    [(et/mark-added
                                      (et/exists
                                       et/exists-k
                                       (fn [ex]
                                         (et/edit-node
                                          et/edit-node-uri ex
                                          et/edit-node-properties {"http://schema.org/name"
                                                                   [(et/mark-added (et/make-literal-string "Foobar"))]}))))]}))
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

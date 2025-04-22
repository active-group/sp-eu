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
                                    [(et/make-same (et/make-literal-string "Foobar"))]
                                    "http://schema.org/description"
                                    [(et/make-same (et/make-literal-string "Descr"))]}))))

  ;; literal added
  (is (= [(change/make-add
           (change/make-statement "http://example.org/a"
                                  "http://schema.org/name"
                                  (tree/make-literal-string "Foobar")))]
         (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "http://example.org/a"
           et/edit-node-properties {"http://schema.org/name"
                                    [(et/make-added (et/make-literal-string "Foobar"))]}))))

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
  (is (= [(change/make-delete
           (change/make-statement "http://example.org/a"
                                  "http://schema.org/name"
                                  (tree/make-literal-string "Foobar")))
          (change/make-add
           (change/make-statement "http://example.org/a"
                                  "http://schema.org/name"
                                  (tree/make-literal-string "Barfoo")))]
         (et/edit-tree-changeset
          (et/edit-node
           et/edit-node-uri "http://example.org/a"
           et/edit-node-properties {"http://schema.org/name"
                                    [(et/make-maybe-changed
                                      (et/make-literal-string "Foobar")
                                      (et/make-literal-string "Barfoo"))]}))))

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
                                                                [(et/make-added (et/make-literal-string "Foobar"))]}))]}))))

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
                                                                [(et/make-same (et/make-literal-string "Foobar"))]})
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
                                                                     [(et/make-deleted (et/make-literal-string "Foobar"))]}))]}))))))

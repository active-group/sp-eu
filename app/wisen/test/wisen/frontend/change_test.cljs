(ns wisen.frontend.change-test
  (:require [wisen.frontend.tree :as tree]
            [wisen.frontend.change :as change]
            [cljs.test :refer-macros [deftest is testing async]]))

(defn- lit [s]
  (tree/literal-string tree/literal-string-value s))

(defn- prop [p o]
  (tree/property
   tree/property-predicate p
   tree/property-object o))

(defn statement [s p o]
  (change/statement change/statement-subject s
                    change/statement-predicate p
                    change/statement-object o))

(deftest tree-statements-test

  (let [t (tree/node tree/node-uri "root"
                     tree/node-properties [])]
    (is (= #{} (set (change/tree-statements t)))))

  (let [t (tree/node tree/node-uri "root"
                     tree/node-properties [(prop "pred1" (lit "lit1"))
                                           (prop "pred2" (lit "lit2"))
                                           (prop "pred2" (lit "lit3"))])]
    (is (= #{(statement "root" "pred1" (lit "lit1"))
             (statement "root" "pred2" (lit "lit2"))
             (statement "root" "pred2" (lit "lit3"))}
           (set (change/tree-statements t)))))

  (let [t (tree/node tree/node-uri "root"
                     tree/node-properties [(prop "pred1" (tree/node
                                                          tree/node-uri "ch1"
                                                          tree/node-properties []))])]
    (is (= #{(statement "root" "pred1" "ch1")}
           (set (change/tree-statements t)))))

  (let [t (tree/node tree/node-uri "root"
                     tree/node-properties [(prop
                                            "pred1"
                                            (tree/node
                                             tree/node-uri "ch1"
                                             tree/node-properties [(prop
                                                                    "pred2"
                                                                    (tree/ref tree/ref-uri "root"))]))])]
    (is (= #{(statement "root" "pred1" "ch1")
             (statement "ch1" "pred2" "root")}
           (set (change/tree-statements t)))))
  )


(deftest compare-statement-test
  ;; sanity check
  (is (= -1 (compare "o" "q")))

  (is (= 0 (change/compare-statement (statement "s" "p" "o")
                                     (statement "s" "p" "o"))))

  (is (= -1 (change/compare-statement (statement "s" "p" "o")
                                      (statement "s" "p" "q"))))

  (is (= -1 (change/compare-statement (statement "s" "a" "o")
                                      (statement "s" "b" "o"))))

  (is (= -1 (change/compare-statement (statement "a" "p" "o")
                                      (statement "b" "p" "o")))))

(deftest delta-tree-test

  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [])]
    (is (= #{} (set (change/delta-tree t1 t1)))))

  ;; simple add
  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [])
        t2 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))])]
    (is (= #{(change/make-add (statement "root"
                                         "pred1"
                                         (lit "lit1")))}
           (set (change/delta-tree t1 t2)))))

  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))])]
    (is (= #{}
           (set (change/delta-tree t1 t1)))))

  ;; simple delete
  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))])
        t2 (tree/node tree/node-uri "root"
                      tree/node-properties [])]
    (is (= #{(change/make-delete (statement "root"
                                            "pred1"
                                            (lit "lit1")))}
           (set (change/delta-tree t1 t2)))))

  ;; simple delete and add
  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))])
        t2 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit2"))])]
    (is (= #{(change/make-delete (statement "root"
                                            "pred1"
                                            (lit "lit1")))
             (change/make-add (statement "root"
                                         "pred1"
                                         (lit "lit2")))}
           (set (change/delta-tree t1 t2)))))

  ;; complex delete
  (let [t1 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))
                                            (prop "pred2" (lit "lit2"))
                                            (prop "pred3" (lit "lit3"))
                                            (prop "pred4" (lit "lit4"))])
        t2 (tree/node tree/node-uri "root"
                      tree/node-properties [(prop "pred1" (lit "lit1"))
                                            (prop "pred2" (lit "lit2"))
                                            (prop "pred4" (lit "lit4"))])]
    (is (= #{(change/make-delete (statement "root"
                                            "pred3"
                                            (lit "lit3")))}
           (set (change/delta-tree t1 t2))))))

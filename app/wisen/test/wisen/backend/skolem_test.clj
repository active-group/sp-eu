(ns wisen.backend.skolem-test
  (:require [clojure.test :refer [deftest testing is]]
            [wisen.backend.skolem2 :as skolem]
            [wisen.common.change-api :as change-api]))

(deftest skolemize-change-test
  (is (=
       [(change-api/make-add
         (change-api/make-statement
          "sub" "pred" "obj"))]
       (skolem/skolemize-change
        (change-api/make-add
         (change-api/make-statement
          "sub" "pred" "obj"))
        {})))

  (is (=
       [(change-api/make-add
         (change-api/make-statement
          "sub" "pred" "obj"))]
       (skolem/skolemize-change
        (change-api/make-add
         (change-api/make-statement
          0 "pred" "obj"))
        {0 "sub"})))

  (let [orig (change-api/make-with-blank-node
              [(change-api/make-add
                (change-api/make-statement "sub" "pred" 0))
               (change-api/make-add
                (change-api/make-statement 0 "pred2" "obj"))])
        skolemized (skolem/skolemize-change orig {})]
    (is (= 2 (count skolemized)))
    (let [stmt1 (change-api/add-statement (first skolemized))
          stmt2 (change-api/add-statement (second skolemized))]
      (is (= "sub" (change-api/statement-subject stmt1)))
      (is (= "pred" (change-api/statement-predicate stmt1)))
      (is (= "pred2" (change-api/statement-predicate stmt2)))
      (is (= "obj" (change-api/statement-object stmt2)))
      (is (string? (change-api/statement-object stmt1)))
      (is (= (change-api/statement-object stmt1)
             (change-api/statement-subject stmt2)))
      )))

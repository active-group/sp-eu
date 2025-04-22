(ns wisen.frontend.change-api-test
  (:require [wisen.common.change-api :as ch]
            [cljs.test :refer-macros [deftest is testing async]]))

(deftest literal-or-uri-test
  (is (= (ch/literal-or-uri<->edn "some-uri")
         ["uri" "some-uri"]))

  (is (= (ch/literal-or-uri<->edn nil ["uri" "some-uri"])
         "some-uri"))

  (is (= (ch/literal-or-uri<->edn (ch/make-literal-string "some-string"))
         ["literal-string" "some-string"]))

  (is (= (ch/literal-or-uri<->edn nil ["literal-string" "some-string"])
         (ch/make-literal-string "some-string"))))

(deftest change-test
  (is (= (ch/change<->edn (ch/make-add
                           (ch/make-statement "sub"
                                              "pred"
                                              "obj")))
         ["add"
          {:subject "sub"
           :predicate "pred"
           :object ["uri" "obj"]}]))

  (is (= (ch/change<->edn nil ["add"
                               {:subject "sub"
                                :predicate "pred"
                                :object ["uri" "obj"]}])
         (ch/make-add
          (ch/make-statement "sub"
                             "pred"
                             "obj"))))

  (is (= (ch/change<->edn (ch/make-delete
                           (ch/make-statement "sub"
                                              "pred"
                                              "obj")))
         ["delete"
          {:subject "sub"
           :predicate "pred"
           :object ["uri" "obj"]}]))

  (is (= (ch/change<->edn nil ["delete"
                               {:subject "sub"
                                :predicate "pred"
                                :object ["uri" "obj"]}])
         (ch/make-delete
          (ch/make-statement "sub"
                             "pred"
                             "obj"))))

  (is (= (ch/change<->edn (ch/make-with-blank-node
                           [(ch/make-add
                             (ch/make-statement "sub"
                                                "pred"
                                                "obj"))]))
         ["with-blank-node"
          [["add"
            {:subject "sub"
             :predicate "pred"
             :object ["uri" "obj"]}]]]))

  (is (= (ch/change<->edn nil ["with-blank-node"
                               [["add"
                                 {:subject 0
                                  :predicate "pred"
                                  :object ["uri" "obj"]}]]])
         (ch/make-with-blank-node
          [(ch/make-add
            (ch/make-statement 0
                               "pred"
                               "obj"))]))))


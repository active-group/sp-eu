(ns wisen.frontend.change-api-test
  (:require [wisen.common.change-api :as ch]
            [cljs.test :refer-macros [deftest is testing async]]))

(deftest leaf-test
  (is (= (ch/leaf<->edn "some-uri")
         ["uri" "some-uri"]))

  (is (= (ch/leaf<->edn nil ["uri" "some-uri"])
         "some-uri"))

  (is (= (ch/leaf<->edn (ch/make-literal-string "some-string"))
         ["literal-string" "some-string"]))

  (is (= (ch/leaf<->edn nil ["literal-string" "some-string"])
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
                           123
                           [(ch/make-add
                             (ch/make-statement "sub"
                                                "pred"
                                                "obj"))]))
         ["with-blank-node"
          {:existential 123
           :changes [["add"
                      {:subject "sub"
                       :predicate "pred"
                       :object ["uri" "obj"]}]]}]))

  (is (= (ch/change<->edn nil ["with-blank-node"
                               {:existential 123
                                :changes [["add"
                                           {:subject 123
                                            :predicate "pred"
                                            :object ["uri" "obj"]}]]}])
         (ch/make-with-blank-node
          123
          [(ch/make-add
            (ch/make-statement 123
                               "pred"
                               "obj"))]))))


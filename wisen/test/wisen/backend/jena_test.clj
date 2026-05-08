(ns wisen.backend.jena-test
  (:require [clojure.test :refer [deftest is]]
            [wisen.backend.jena :as jena]
            [wisen.common.change-api :as change-api]))

(deftest model->nt-test
  (let [mdl (-> (jena/empty-model)
                (jena/add-statement!
                 (change-api/make-statement
                  "urn:foo"
                  "urn:bar"
                  "urn:bla")))]

    (is (= "<urn:foo> <urn:bar> <urn:bla> .\n"
           (jena/model->nt mdl))))

  (let [mdl (-> (jena/empty-model)
                (jena/add-statement!
                 (change-api/make-statement
                  "urn:aaa"
                  "urn:bb1"
                  "urn:cc1"))
                (jena/add-statement!
                 (change-api/make-statement
                  "urn:aaa"
                  "urn:bb2"
                  "urn:cc2")))]

    (is (= "<urn:aaa> <urn:bb1> <urn:cc1> .\n<urn:aaa> <urn:bb2> <urn:cc2> .\n"
           (jena/model->nt mdl)))))

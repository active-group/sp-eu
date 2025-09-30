(ns wisen.backend.repository-test
  (:require [clojure.test :refer [deftest is]]
            [wisen.backend.repository :as r]))

(deftest merge-folders-test
  (is (=
       {"model.json" "{\n    \"@id\": \"http://x.com\",\n    \"http://schema.org/name\": \"Quarki\"\n}\n"}
       (r/merge-folders {"model.json" "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}"}
                        {"model.json" "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}"}
                        {"model.json" "{\"@id\": \"http://x.com\", \"http://schema.org/name\": \"Quarki\"}"})))

  ;; multiple json-ld files
  (is (=

       {"model.json"
        "{\n    \"@id\": \"http://x.com\",\n    \"http://schema.org/email\": \"bla@bar.com\",\n    \"http://schema.org/name\": \"Quarki\"\n}\n"}

       (r/merge-folders

        {"m1.json" "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}"
         "m2.json" "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"foo@bar.com\"}"}

        {"m1.json" "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}"
         "m2.json" "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"bla@bar.com\"}"}

        {"m1.json" "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Quarki\"}"
         "m2.json" "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"foo@bar.com\"}"}))))

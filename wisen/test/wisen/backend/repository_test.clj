(ns wisen.backend.repository-test
  (:require [clojure.test :refer [deftest is]]
            [wisen.backend.repository :as r]
            [wisen.backend.jena :as jena]
            [wisen.backend.git-tree :as git-tree]
            [wisen.common.change-api :as change-api]))

(deftest folder->model-test
  (is (= "<http://x.com> <http://schema.org/name> \"Quarki\" .\n"
         (jena/model->nt
          (r/folder->model
           (git-tree/make-folder
            {"foo.json"
             (git-tree/make-file
              "{\n    \"@id\": \"http://x.com\",\n    \"http://schema.org/name\": \"Quarki\"\n}\n")})))))

  (is (= "<http://x.com> <http://schema.org/description> \"foo\" .\n<http://x.com> <http://schema.org/name> \"Quarki\" .\n"
         (jena/model->nt
          (r/folder->model
           (git-tree/make-folder
            {"foo.json"
             (git-tree/make-file
              "{\n    \"@id\": \"http://x.com\",\n    \"http://schema.org/name\": \"Quarki\"\n}\n")
             "foo.nt"
             (git-tree/make-file
              "<http://x.com> <http://schema.org/description> \"foo\" .\n")}))))))

(deftest merge-folders-test
  ;; Note: upon merge we transform from json-ld to NTRIPLES
  (is (=
       (git-tree/make-folder
        {"24.nt"
         (git-tree/make-file
          "<http://x.com> <http://schema.org/name> \"Quarki\" .\n")})
       (r/merge-folders (git-tree/make-folder
                         {"model.json"
                          (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}")})
                        (git-tree/make-folder
                         {"model.json"
                          (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}")})
                        (git-tree/make-folder
                         {"model.json"
                          (git-tree/make-file "{\"@id\": \"http://x.com\", \"http://schema.org/name\": \"Quarki\"}")}))))

  ;; multiple json-ld files
  (is (=

       (git-tree/make-folder
        {"24.nt"
         (git-tree/make-file
          "<http://x.com> <http://schema.org/name> \"Quarki\" .\n<http://x.com> <http://schema.org/email> \"bla@bar.com\" .\n"
          #_"{\n    \"@id\": \"http://x.com\",\n    \"http://schema.org/email\": \"bla@bar.com\",\n    \"http://schema.org/name\": \"Quarki\"\n}\n")})

       (r/merge-folders

        (git-tree/make-folder
         {"m1.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}")
          "m2.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"foo@bar.com\"}")})

        (git-tree/make-folder
         {"m1.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Marki\"}")
          "m2.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"bla@bar.com\"}")})

        (git-tree/make-folder
         {"m1.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/name\": \"Quarki\"}")
          "m2.json" (git-tree/make-file "{\"@id\": \"http://x.com\",\"http://schema.org/email\": \"foo@bar.com\"}")})))))

(defmacro apply-changeset-commutes [changeset folder]

  ;;  folder1 ----[r/apply-changeset!]---> folder2
  ;;    |                                    |
  ;;   [folder->model]                      [folder->model]
  ;;    |                                    |
  ;;    v                                    v
  ;;  model1 ----[jena/apply-changeset!]---> model2

  `(is (= (jena/model->nt
           (jena/apply-changeset!
            (r/folder->model ~folder)
            ~changeset))

          (jena/model->nt
           (r/folder->model
            (r/folder-apply-changeset ~changeset ~folder))))))

(deftest folder-apply-changeset-test
  (apply-changeset-commutes
   []
   (git-tree/empty-folder))

  (apply-changeset-commutes
   [(change-api/make-add
     (change-api/make-statement
      "urn:markus"
      "urn:says"
      (change-api/make-literal-string "hi")))]
   (git-tree/empty-folder))

  (apply-changeset-commutes
   [(change-api/make-delete
     (change-api/make-statement
      "urn:markus"
      "urn:says"
      (change-api/make-literal-string "hi")))]
   (-> (git-tree/empty-folder)
       (git-tree/assoc
        "30.nt" ;; "urn:markus" -> "30.nt"
        (git-tree/make-file "<urn:markus> <urn:says> \"hi\" .")
        )))

  (apply-changeset-commutes
   [(change-api/make-delete
     (change-api/make-statement
      "urn:markus"
      "urn:says"
      (change-api/make-literal-string "hi")))]
   (-> (git-tree/empty-folder)
       (git-tree/assoc
        "30.nt" ;; "urn:markus" -> "30.nt"
        (git-tree/make-file "<urn:bla> <urn:foo> <urn:bar> .\n
                             <urn:markus> <urn:says> \"hi\" .")
        )))

  ;; unaffected files must not be touched
  (apply-changeset-commutes
   [(change-api/make-delete
     (change-api/make-statement
      "urn:markus"
      "urn:says"
      (change-api/make-literal-string "hi")))]
   (-> (git-tree/empty-folder)
       (git-tree/assoc
        "30.nt" ;; "urn:markus" -> "30.nt"
        (git-tree/make-file "<urn:markus> <urn:says> \"hi\" ."))
       (git-tree/assoc
        "1.nt"
        (git-tree/make-file
         "<urn:bla> <urn:foo> <urn:bar> ."))))

  (let [folder (-> (git-tree/empty-folder)
                   (git-tree/assoc
                    "30.nt" ;; "urn:markus" -> "30.nt"
                    (git-tree/make-file "<urn:markus> <urn:says> \"hi\" ."))
                   (git-tree/assoc
                    "1.nt"
                    (git-tree/make-file
                     "OBJ-ID-1"
                     "<urn:bla> <urn:foo> <urn:bar> .")))
        changeset [(change-api/make-delete
                    (change-api/make-statement
                     "urn:markus"
                     "urn:says"
                     (change-api/make-literal-string "hi")))]]

    (is (= (-> (git-tree/empty-folder)
               (git-tree/assoc
                "30.nt" ;; "urn:markus" -> "30.nt"
                (git-tree/make-file ""))
               (git-tree/assoc
                "1.nt"
                (git-tree/make-file
                 "OBJ-ID-1"
                 "<urn:bla> <urn:foo> <urn:bar> .")))
           (r/folder-apply-changeset changeset folder)))))

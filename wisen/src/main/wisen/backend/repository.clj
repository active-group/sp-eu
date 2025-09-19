(ns wisen.backend.repository
  (:import [java.io File]
           [java.nio.file Files])
  (:require [wisen.backend.git :as git]
            [wisen.common.change-api :as change-api]
            [wisen.backend.jena :as jena]
            [wisen.backend.skolem :as skolem]
            [active.data.realm :as realm]))

(def ^:private read-git
  (atom {}))

(defn with-read-git [remote-uri f]
  (let [g (or (get @read-git remote-uri)
              (let [g (git/clone! remote-uri)]
                (swap! read-git
                       (fn [m]
                         (assoc m remote-uri g)))
                g))]
    (git/pull! g)
    (f g)))

(defn- changes [base local remote]
  (change-api/union-changeset
   (jena/changeset base local)
   (jena/changeset base remote)))

(defn merge-strings
  "The implementation of our mergetool in terms of strings"
  [base local remote]
  (let [chngs (changes
               (jena/string->model base)
               (jena/string->model local)
               (jena/string->model remote))]
    (jena/model->string
     (jena/apply-changeset! base chngs))))

#_(defn mergetool! [base-file local-file remote-file merged-file]
  (let [base (slurp base-file)
        local (slurp local-file)
        remote (slurp remote-file)]
    (spit merged-file
          (merge-strings base local remote))))

(defn- pull-push! [git]
  (or (git/push! git)
      (do
        (git/pull! git merge-strings)
        (recur git))))

(def ^:private model-filename
  "model.json")

(defn- model-path [git]
  (str (git/git-directory git)
       "/" model-filename))



;; --- API ---

(declare write!)

(defn head! [prefix repo-uri]
  (with-read-git repo-uri
    (fn [g]
      (let [head-candidate (git/head g)
            s (git/get! g head-candidate model-filename)
            mdl (jena/string->model s)
            [mdl-skolemized changed?] (skolem/skolemize-model mdl prefix)]

        ;; write back when changed and return new commit-id as head
        (if changed?
          (write! repo-uri head-candidate mdl-skolemized "Skolemize")
          head-candidate)))))

#_(head! "file:///Users/markusschlegel/Desktop/tmp/repo")
;; => "ec00eec6f253fe8ccc15429d4c54301854d6a651"

(defn read! [repo-uri commit-id]
  (with-read-git
    repo-uri
    (fn [g]
      (let [s (git/get! g commit-id model-filename)]
        (jena/string->model s)))))

#_(read "file:///Users/markusschlegel/Desktop/tmp/repo"
       "b10008d5039214a4bdc03840820a61e8d9bf5e10")

(defn write! [repo-uri commit-id model commit-message]
  (let [git (git/clone! repo-uri)]

    (git/checkout! git commit-id)

    (let [path (model-path git)]

      ;; Render back to model.json string
      (spit path (jena/model->string model))

      ;; Add
      (git/add! git model-filename)

      ;; Commit
      (git/commit! git commit-message)

      ;; Push
      (let [result-commit-id (pull-push! git)]

        ;; Cleanup
        (git/kill! git)

        result-commit-id))))

(defn change!
  "`base-commit-id` denotes an RDF graph `g`. `changeset` denotes a
  function turning an RDF graph `g` into an RDF graph `g'`. If
  successful, this function returns a commit-id that denotes the
  application of `changeset` onto `g`.

  write (g : Graph) (f : Graph -> Graph) = f g"
  [repo-uri base-commit-id changeset commit-message]

  {:pre [(realm/contains? realm/string repo-uri)
         (realm/contains? git/commit-id base-commit-id)
         (realm/contains? change-api/changeset changeset)]
   :post [(realm/contains? git/commit-id %)]}

  (let [git (git/clone! repo-uri)]

    (git/checkout! git base-commit-id)

    (let [path (model-path git)
          s (slurp path)
          model (jena/string->model s)
          model* (jena/apply-changeset! model changeset)]

      ;; Render back to model.json string
      (spit path (jena/model->string model*))

      ;; Add
      (git/add! git model-filename)

      ;; Commit
      (git/commit! git commit-message)

      ;; Push
      (let [result-commit-id (pull-push! git)]

        ;; Cleanup
        (git/kill! git)

        result-commit-id))))

#_(write! "file:///Users/markusschlegel/Desktop/tmp/repo"
        "f4f88fe4f8d867bd4816c6e2b3859316c16fea37"
        [(change-api/make-add
          (change-api/make-statement
           "http://example.org/123"
           "http://schema.org/name"
           (change-api/make-literal-string "Foobar123")))])

#_#_(defn diff

  [repo-uri from-commit-id to-commit-id]

  {:pre [(realm/contains? realm/string repo-uri)
         (realm/contains? git/commit-id from-commit-id)
         (realm/contains? git/commit-id to-commit-id)]
   :post [(realm/contains? change-api/changeset %)]}

  (let [from-model (read repo-uri from-commit-id)
        to-model (read repo-uri to-commit-id)]
    (jena/changeset from-model to-model)))

(diff "file:///Users/markusschlegel/Desktop/tmp/repo"
      "b10008d5039214a4bdc03840820a61e8d9bf5e10"
      "ec00eec6f253fe8ccc15429d4c54301854d6a651")

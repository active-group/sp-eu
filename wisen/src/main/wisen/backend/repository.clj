(ns wisen.backend.repository
  (:import [java.io File]
           [java.nio.file Files])
  (:require [wisen.backend.git :as git]
            [wisen.common.change-api :as change-api]
            [wisen.backend.jena :as jena]
            [wisen.backend.skolem :as skolem]
            [active.data.realm :as realm]))

;; This namespace realizes the idea that a tuple of repository-uri and
;; commit-id just represent a (RDF) model. `head!` is the only
;; neccessarily unpure function potentially returning a different
;; result every time. `read!` is extensionally pure. `write!` is
;; impure but I think it could be made extensionally pure.

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

(defn read! [repo-uri commit-id]
  (with-read-git
    repo-uri
    (fn [g]
      (let [s (git/get! g commit-id model-filename)]
        (jena/string->model s)))))

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

#_(defn diff

  [repo-uri from-commit-id to-commit-id]

  {:pre [(realm/contains? realm/string repo-uri)
         (realm/contains? git/commit-id from-commit-id)
         (realm/contains? git/commit-id to-commit-id)]
   :post [(realm/contains? change-api/changeset %)]}

  (let [from-model (read repo-uri from-commit-id)
        to-model (read repo-uri to-commit-id)]
    (jena/changeset from-model to-model)))

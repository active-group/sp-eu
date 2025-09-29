(ns wisen.backend.repository
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

(defn- just-theirs [_base _ours theirs]
  theirs)

(defn with-read-git [remote-uri f]
  (let [g (or (get @read-git remote-uri)
              (let [g (git/clone! remote-uri)]
                (swap! read-git
                       (fn [m]
                         (assoc m remote-uri g)))
                g))]
    (git/join-master! g (git/fetch! g) just-theirs)
    (f g)))

(def ^:private model-filename
  "model.json")

(defn- changes [base local remote]
  (change-api/union-changeset
   (jena/changeset base local)
   (jena/changeset base remote)))

(defn- folder->model [folder]
  (jena/string->model (get folder model-filename)))

(defn- model->folder [model]
  {model-filename (jena/model->string model)})

(defn- via-3 [in out f]
  (fn [x y z]
    (out
     (f (in x)
        (in y)
        (in z)))))

(defn- merge-models [base-model ours-model theirs-model]
  (let [chngs (changes base-model ours-model theirs-model)]
    (jena/apply-changeset! base-model chngs)))

(def merge-folders
  (via-3 folder->model
         model->folder
         merge-models))


;; --- API ---

(declare write!)

(defn head! [prefix repo-uri]
  (with-read-git repo-uri
    (fn [g]
      (let [head-candidate (git/head! g)
            folder (git/get! g head-candidate)
            s (get folder model-filename)
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
      (let [folder (git/get! g commit-id)
            s (get folder model-filename)]
        (jena/string->model s)))))

(defn write! [repo-uri commit-id model commit-message]
  (let [git (git/clone! repo-uri)
        change-commit-id (git/commit! git
                                      {model-filename (jena/model->string model)}
                                      commit-message
                                      commit-id)
        new-head (git/join-master! git
                                   change-commit-id
                                   merge-folders)
        push-commit (git/sync-master! git merge-folders)]

    (git/kill! git)
    push-commit))

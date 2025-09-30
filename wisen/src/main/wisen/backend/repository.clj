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

(defn- is-json-ld-filename? [s]
  (boolean (re-matches #".*\.json(ld)?$" s)))

(defn- folder->model [folder]
  (reduce (fn [model [filename file-tree]]
            (if (and (string? file-tree)
                     (is-json-ld-filename? filename))
              (jena/union model
                          (jena/string->model file-tree))
              model))
          jena/empty-model
          folder))

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
            mdl (folder->model folder)
            [mdl-skolemized changed?] (skolem/skolemize-model mdl prefix)]

        ;; write back when changed and return new commit-id as head
        (if changed?
          (write! repo-uri head-candidate mdl-skolemized "Skolemize")
          head-candidate)))))

(defn read! [repo-uri commit-id]
  (with-read-git
    repo-uri
    (fn [g]
      (let [folder (git/get! g commit-id)]
        (folder->model folder)))))

(defn write! [repo-uri commit-id model commit-message]
  (let [git (git/clone! repo-uri)
        change-commit-id (git/commit! git
                                      (model->folder model)
                                      commit-message
                                      commit-id)
        new-head (git/join-master! git
                                   change-commit-id
                                   merge-folders)
        push-commit (git/sync-master! git merge-folders)]

    (git/kill! git)
    push-commit))

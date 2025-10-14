(ns wisen.backend.repository
  (:require [wisen.backend.git :as git]
            [wisen.backend.git-tree :as git-tree]
            [wisen.common.change-api :as change-api]
            [wisen.backend.jena :as jena]))

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

(defn- is-nt-filename? [s]
  (boolean (re-matches #".*\.nt$" s)))

(defn- is-jsonld-filename? [s]
  (boolean (re-matches #".*\.json(ld)?$" s)))

(defn folder->model [folder]
  (reduce (fn [model [filename file-tree]]
            (if (git-tree/file? file-tree)
              (cond
                (is-nt-filename? filename)
                (jena/union model
                            (jena/nt->model (git-tree/file-string file-tree)))

                (is-jsonld-filename? filename)
                (jena/union model
                            (jena/jsonld->model (git-tree/file-string file-tree)))

                :else
                model)
              model))
          (jena/empty-model)
          (git-tree/folder-contents folder)))

(defn- uri-classifier [uri]
  (mod (hash uri)
       50))

(defn- classifier-filename [classifier]
  (str classifier ".nt"))

(defn- change-statement [stmt]
  (cond
    (change-api/add? stmt)
    (change-api/add-statement stmt)

    (change-api/delete? stmt)
    (change-api/delete-statement stmt)))

(defn- statement-filename [stmt]
  (let [subj (change-api/statement-subject stmt)
        uri subj
        cls (uri-classifier uri)]
    (classifier-filename cls)))

(defn- jena-statement-filename [stmt]
  (classifier-filename
   (uri-classifier (.getURI (.getSubject stmt)))))

(defn- statements->string-jena [stmts]
  (jena/model->nt
   (let [mdl (jena/empty-model)]
     (.add mdl stmts)
     mdl)))

(defn- model-statements [model]
  (iterator-seq
   (.listStatements model)))

(defn- statements->folder [stmts]
  (reduce (fn [folder [filename stmts]]
            (git-tree/assoc folder
                            filename
                            (git-tree/make-file
                             (statements->string-jena stmts))))
          (git-tree/empty-folder)
          (group-by jena-statement-filename stmts)))

(defn model->folder [model]
  (statements->folder (model-statements model)))

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
  (with-read-git repo-uri git/head!))

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

(defn update! [repo-uri commit-id tree-f & [commit-message]]
  (with-read-git
    repo-uri
    (fn [git]
      (let [git-tree-f (git/update git tree-f commit-message)]
        (let [change-commit-id (git-tree-f commit-id)
              new-head (git/join-master! git change-commit-id merge-folders)
              push-commit (git/sync-master! git merge-folders)]
          push-commit)))))


(defn- folder-apply-changeset* [folder [filename changeset]]
  (git-tree/update folder
                   filename
                   (fn [file-tree]
                     (let [model (if (git-tree/file? file-tree)
                                   (jena/nt->model (git-tree/file-string file-tree))
                                   (jena/empty-model))]
                       (git-tree/make-file
                        (jena/model->nt
                         (jena/apply-changeset!
                          model
                          changeset)))))))

(defn folder-apply-changeset [changeset folder]
  (reduce folder-apply-changeset*
          folder
          (group-by
           (comp statement-filename change-statement)
           changeset)))

(defn folder-apply-changeset! [repo-uri commit-id changeset]
  (update! repo-uri commit-id (partial folder-apply-changeset changeset)))

(defn update!! [repo-uri commit-id f commit-message]
  (update! repo-uri
           commit-id
           (comp model->folder
                 f
                 folder->model)
           commit-message))

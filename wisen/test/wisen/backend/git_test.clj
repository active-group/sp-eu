(ns wisen.backend.git-test
  (:require [clojure.test :refer [deftest is]]
            [wisen.backend.git :as git])
  (:import
   (java.io File)
   (java.nio.file Files)
   (java.nio.charset StandardCharsets)
   (org.eclipse.jgit.lib Repository FileMode Constants CommitBuilder TreeFormatter AnyObjectId RefUpdate$Result PersonIdent)
   (org.eclipse.jgit.dircache DirCache DirCacheEditor DirCacheEditor$PathEdit DirCacheEntry)
   (org.eclipse.jgit.revwalk RevWalk RevCommit RevTree)
   (org.eclipse.jgit.revwalk.filter RevFilter)
   (org.eclipse.jgit.treewalk TreeWalk)
   (org.eclipse.jgit.treewalk.filter PathFilter )
   (org.eclipse.jgit.api Git Status AddCommand CommitCommand)
   (org.eclipse.jgit.api.errors RefNotAdvertisedException)
   (org.eclipse.jgit.internal.storage.file FileRepository)
   (org.eclipse.jgit.transport RefSpec RemoteRefUpdate RemoteRefUpdate$Status)))

(defn- create-temp-dir! [name]
  (.toFile
   (Files/createTempDirectory
    name
    (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- insert-string! [repo s]
  (let [inserter (.newObjectInserter repo)
        blob-id (.insert inserter Constants/OBJ_BLOB
                         (.getBytes s "UTF-8"))]
    (.flush inserter)
    blob-id))

(defn- insert-tree-with-file! [repo path blob-id]
  (let [inserter (.newObjectInserter repo)
        formatter (new TreeFormatter)
        _ (.append formatter path FileMode/REGULAR_FILE blob-id)
        tree-id (.insert inserter formatter)]
    (.flush inserter)
    tree-id))

(defn- commit!
  "Create a new commit on top of `parent-commit` with `string-contents`
  at `path`. Returns the resulting commit-id."
  [repo path string-contents message & [parent-commit-1 parent-commit-2]]
  (let [blob-id (insert-string! repo string-contents)
        tree-id (insert-tree-with-file! repo path blob-id)
        alice (new PersonIdent
                   "Speubot"
                   "info@active-group.de"
                   (new java.util.Date 1759137642525)
                   (java.util.TimeZone/getTimeZone "UTC"))]

    (let [commit-builder (new CommitBuilder)]
      (.setTreeId commit-builder tree-id)
      (when parent-commit-1
        (if parent-commit-2
          (.setParentIds commit-builder parent-commit-1 parent-commit-2)
          (.setParentId commit-builder parent-commit-1)))
      (.setAuthor commit-builder alice)
      (.setCommitter commit-builder alice)
      (.setMessage commit-builder message)

      (let [inserter (.newObjectInserter repo)
            commit-id (.insert inserter commit-builder)]
        (.flush inserter)
        commit-id))))

(defn- make-bare-git []
  (let [dir (create-temp-dir! "test")
        g (.call
           (doto (Git/init)
             (.setBare true)
             (.setDirectory dir)))
        repo (.getRepository g)]
    
    (let [first-commit-id (commit! repo
                                   "foo.txt"
                                   "initial"
                                   "initial commit")
          second-commit-id (commit! repo
                                    "foo.txt"
                                    "<2>"
                                    "<2>"
                                    first-commit-id)
          left-commit-id (commit! repo
                                  "foo.txt"
                                  "<l>"
                                  "<l>"
                                  second-commit-id)

          right-commit-id (commit! repo
                                   "foo.txt"
                                   "<r>"
                                   "<r>"
                                   second-commit-id)]

      (let [ref-upd (.updateRef repo "refs/heads/master")]
        (.setNewObjectId ref-upd left-commit-id)
        (.update ref-upd))

      [g
       (.getName first-commit-id)
       (.getName second-commit-id)
       (.getName left-commit-id)
       (.getName right-commit-id)])))

(deftest reproducibility-test
  ;; This test only checks if our test setup code above is working as expected
  (let [[g1 c1] (make-bare-git)
        [g2 c2] (make-bare-git)]
    (is (= c1 c2))))

(deftest head-test
  (let [[g c1 c2 cl cr] (make-bare-git)]
    (is (= cl
           (git/head! g)))))

(deftest get-test
  (let [[g c1 c2 cl cr] (make-bare-git)]

    (is (= {"foo.txt" "initial"}
           (git/get! g c1)))

    (is (= {"foo.txt" "<2>"}
           (git/get! g c2)))

    (is (= {"foo.txt" "<l>"}
           (git/get! g cl)))

    (is (= {"foo.txt" "<r>"}
           (git/get! g cr)))))

(deftest clone-test
  (let [[g-origin c1 c2 cl cr] (make-bare-git)
        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)]
    (is (= cl
           (git/head! g-local)))))

(deftest fetch-test
  (let [[g-origin c1 c2 cl cr] (make-bare-git)

        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)

        next-commit-id (git/commit! g-origin {"foo.txt" "neu"} "message" cl)
        update-res (git/update-master-ref! g-origin cl next-commit-id)
        ]

    (is (git/update-successful? update-res))
    (is (= (git/head! g-origin) next-commit-id))

    (is (= next-commit-id
           (git/fetch! g-local)))

    ;; fetch must not pull
    (is (= cl (git/head! g-local)))))

(deftest commit-test
  (let [[g c1 c2 cl cr] (make-bare-git)
        next-commit-id (git/commit! g {"foo.txt" "neu"} "message" cl)]

    (is (= {"foo.txt" "neu"}
           (git/get! g next-commit-id)))))

(deftest merge-base-test
  (let [[g c1 c2 cl cr] (make-bare-git)]

    (is (= c2
           (git/merge-base g cl cr)))))

(deftest join-test
  ;; c2 wins
  (let [[g c1 c2 cl cr] (make-bare-git)
        merge-commit (git/join! g
                                c1
                                c2
                                (fn [base ours theirs]
                                  {"foo.txt" "merge result"}))]

    (is (= c2
           merge-commit)))

  ;; c2 wins
  (let [[g c1 c2 cl cr] (make-bare-git)
        merge-commit (git/join! g
                                c2
                                c1
                                (fn [base ours theirs]
                                  {"foo.txt" "merge result"}))]

    (is (= c2 merge-commit)))

  ;; merge
  (let [[g c1 c2 cl cr] (make-bare-git)
        merge-commit (git/join! g
                                cl
                                cr
                                (fn [base ours theirs]
                                  {"foo.txt"
                                   (str
                                    (get ours "foo.txt")
                                    (get theirs "foo.txt"))}))]

    (is (= {"foo.txt" "<l><r>"}
           (git/get! g merge-commit)))))

(deftest join-master-test
  ;; fast-forward
  (let [[g c1 c2 cl cr] (make-bare-git)
        repo (.getRepository g)
        next-commit (.getName (commit! repo "foo.txt" "update" "update message" (.resolve repo cl)))]

    (is (= next-commit
           (git/join-master! g
                             next-commit
                             (fn [base ours theirs]
                               {"foo.txt" "merge result"}))))

    (is (= next-commit
           (git/head! g))))

  ;; merge
  (let [[g c1 c2 cl cr] (make-bare-git)]

    (is (= {"foo.txt" "<l><r>"}
           (git/get! g
                     (git/join-master! g
                                       cr
                                       (fn [base ours theirs]
                                         {"foo.txt"
                                          (str
                                           (get ours "foo.txt")
                                           (get theirs "foo.txt"))})))))))

(deftest sync-master-test
  ;; Not diverged
  (let [[g-origin c1 c2 cl cr] (make-bare-git)

        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)

        next-commit-id (git/commit! g-local {"foo.txt" "neu"} "message" cl)
        update-res (git/update-master-ref! g-local cl next-commit-id)

        _ (is (git/update-successful? update-res))

        sync-result (git/sync-master! g-local
                                      (fn [base ours theirs]
                                        {"foo.txt" "merge result"}))]

    

    (is (= sync-result
           next-commit-id))))

(deftest sync-master-diverged-test
  (let [[g-origin c1 c2 cl cr] (make-bare-git)

        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)

        origin-next-commit-id (git/commit! g-origin {"foo.txt" "<origin>"} "message" cl)
        _ (println "origin-next-commit-id: " origin-next-commit-id)
        origin-update-res (git/update-master-ref! g-origin cl origin-next-commit-id)
        _ (is (git/update-successful? origin-update-res))
        _ (is (= origin-next-commit-id (git/head! g-origin)))

        local-next-commit-id (git/commit! g-local {"foo.txt" "<local>"} "message" cl)
        _ (println "local-next-commit-id: " local-next-commit-id)
        local-update-res (git/update-master-ref! g-local cl local-next-commit-id)
        _ (is (git/update-successful? local-update-res))
        _ (is (= local-next-commit-id (git/head! g-local)))

        sync-result (git/sync-master! g-local
                                      (fn [base ours theirs]
                                        {"foo.txt"
                                         (str
                                          (get ours "foo.txt")
                                          (get theirs "foo.txt"))}))]

    

    (is (= {"foo.txt" "<local><origin>"}
           (git/get! g-origin sync-result)))))

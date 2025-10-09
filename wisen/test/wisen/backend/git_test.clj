(ns wisen.backend.git-test
  (:require [clojure.test :refer [deftest is]]
            [wisen.backend.git :as git]
            [wisen.backend.git-tree :as git-tree])
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

    #_(is (= "initial"
             (git-tree/file-string
              (git-tree/get (git/get! g c1)
                            "foo.txt"))))

    (is (= (git-tree/make-folder
            "e1c397a817f537f406d9eedfade7bd57d05a4949"
            {"foo.txt"
             (git-tree/make-file "c72f08c3900d3b64371fe8d74f09624e277be2c6"
                                 "initial")})
           (git/get! g c1)))


    (is (= (git-tree/make-folder
            "a1ad2dc0023fd814cbee2abe5b10170415c329ea"
            {"foo.txt"
             (git-tree/make-file "84f9ee49fe0a58c59fe0508612eb3b59c7761cc4"
                                 "<2>")})
           (git/get! g c2)))

    (is (= (git-tree/make-folder
            "0a44169aa44282e11191413aa680d9eab5c9c6a7"
            {"foo.txt"
             (git-tree/make-file "b5a10de8d5434349e0f9d25d77dec274bdd30837"
                                 "<l>")})
           (git/get! g cl)))

    (is (= (git-tree/make-folder
            "19fd831b0b08a853d0b0fac8cb26a9169fa14dcd"
            {"foo.txt"
             (git-tree/make-file "0344962bd87d7ebe1e571f03c0d08b7e85f4d88d"
                                 "<r>")})
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

        next-commit-id (git/commit! g-origin
                                    (-> (git-tree/empty-folder)
                                        (git-tree/assoc "foo.txt"
                                                        (git-tree/make-file "neu")))
                                    "message"
                                    cl)
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
        next-commit-id (git/commit! g
                                    (-> (git-tree/empty-folder)
                                        (git-tree/assoc "foo.txt"
                                                        (git-tree/make-file "neu")))
                                    "message"
                                    cl)]

    (is (= (git-tree/make-folder
            "cddd7c6d2444a1d0f1e3a1694fecee7a464db730"
            {"foo.txt"
             (git-tree/make-file "3d6ef48cccf032ad21b9180358d000c61166cc09"
                                 "neu")})
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
                                  (-> (git-tree/empty-folder)
                                      (git-tree/assoc "foo.txt"
                                                      (git-tree/make-file "merge-result")))))]

    (is (= c2
           merge-commit)))

  ;; c2 wins
  (let [[g c1 c2 cl cr] (make-bare-git)
        merge-commit (git/join! g
                                c2
                                c1
                                (fn [base ours theirs]
                                  (-> (git-tree/empty-folder)
                                      (git-tree/assoc "foo.txt"
                                                      (git-tree/make-file "merge-result")))))]

    (is (= c2 merge-commit)))

  ;; merge
  (let [[g c1 c2 cl cr] (make-bare-git)
        merge-commit (git/join! g
                                cl
                                cr
                                (fn [base ours theirs]
                                  (-> (git-tree/empty-folder)
                                      (git-tree/assoc "foo.txt"
                                                      (git-tree/make-file
                                                       (str (git-tree/file-string
                                                             (git-tree/get ours "foo.txt"))
                                                            (git-tree/file-string
                                                             (git-tree/get theirs "foo.txt"))))))))]

    (is (= (git-tree/make-folder
            "6b00705753c25719db1f84d8608da0a8a153bd24"
            {"foo.txt"
             (git-tree/make-file "9984b19f56670a42be817fce1e61b4e05d665884"
                                 "<l><r>")})
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
                               (-> (git-tree/empty-folder)
                                   (git-tree/assoc "foo.txt"
                                                   (git-tree/make-file "merge-result")))))))

    (is (= next-commit
           (git/head! g))))

  ;; merge
  (let [[g c1 c2 cl cr] (make-bare-git)]

    (is (= (git-tree/make-folder
            "6b00705753c25719db1f84d8608da0a8a153bd24"
            {"foo.txt"
             (git-tree/make-file "9984b19f56670a42be817fce1e61b4e05d665884"
                                 "<l><r>")})
           (git/get! g
                     (git/join-master! g
                                       cr
                                       (fn [base ours theirs]
                                         (-> (git-tree/empty-folder)
                                             (git-tree/assoc "foo.txt"
                                                             (git-tree/make-file
                                                              (str (git-tree/file-string
                                                                    (git-tree/get ours "foo.txt"))
                                                                   (git-tree/file-string
                                                                    (git-tree/get theirs "foo.txt")))))))))))))

(deftest sync-master-test
  ;; Not diverged
  (let [[g-origin c1 c2 cl cr] (make-bare-git)

        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)

        next-commit-id (git/commit! g-local
                                    (-> (git-tree/empty-folder)
                                        (git-tree/assoc "foo.txt"
                                                        (git-tree/make-file "neu")))
                                    "message"
                                    cl)
        update-res (git/update-master-ref! g-local cl next-commit-id)

        _ (is (git/update-successful? update-res))

        sync-result (git/sync-master! g-local
                                      (fn [base ours theirs]
                                        (-> (git-tree/empty-folder)
                                            (git-tree/assoc "foo.txt"
                                                            (git-tree/make-file "merge-result")))))]

    

    (is (= sync-result
           next-commit-id))))

(deftest sync-master-diverged-test
  (let [[g-origin c1 c2 cl cr] (make-bare-git)

        remote-url (str "file://" (git/git-directory g-origin))
        g-local (git/clone! remote-url)

        origin-next-commit-id (git/commit! g-origin
                                           (-> (git-tree/empty-folder)
                                               (git-tree/assoc "foo.txt"
                                                               (git-tree/make-file "<origin>")))
                                           "message"
                                           cl)
        _ (println "origin-next-commit-id: " origin-next-commit-id)
        origin-update-res (git/update-master-ref! g-origin cl origin-next-commit-id)
        _ (is (git/update-successful? origin-update-res))
        _ (is (= origin-next-commit-id (git/head! g-origin)))

        local-next-commit-id (git/commit! g-local
                                          (-> (git-tree/empty-folder)
                                              (git-tree/assoc "foo.txt"
                                                              (git-tree/make-file "<local>")))
                                          "message"
                                          cl)
        _ (println "local-next-commit-id: " local-next-commit-id)
        local-update-res (git/update-master-ref! g-local cl local-next-commit-id)
        _ (is (git/update-successful? local-update-res))
        _ (is (= local-next-commit-id (git/head! g-local)))

        sync-result (git/sync-master! g-local
                                      (fn [base ours theirs]
                                        (-> (git-tree/empty-folder)
                                            (git-tree/assoc "foo.txt"
                                                            (git-tree/make-file
                                                             (str (git-tree/file-string
                                                                   (git-tree/get ours "foo.txt"))
                                                                  (git-tree/file-string
                                                                   (git-tree/get theirs "foo.txt"))))))))]

    

    (is (= (git-tree/make-folder
            "9f1d34ec3b321067205cb2c0b90e63a7b2c2803f"
            {"foo.txt"
             (git-tree/make-file "d2e801cdb7c9e1125b6b759d7bfb6c4a385393c7"
                                 "<local><origin>")})
           (git/get! g-origin sync-result)))))

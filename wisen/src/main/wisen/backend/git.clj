(ns wisen.backend.git
  (:require [active.data.record :refer [def-record]]
            [clojure.java.io :as io]
            [wisen.common.prefix :refer [prefix]]
            [active.data.realm :as realm]
            [active.clojure.logger.event :as event-logger]
            [clojure.java.io :as io])
  (:import
   (java.io File)
   (java.nio.file Files)
   (java.nio.charset StandardCharsets)
   (org.eclipse.jgit.lib Repository FileMode Constants CommitBuilder TreeFormatter)
   (org.eclipse.jgit.dircache DirCache DirCacheEditor DirCacheEditor$PathEdit DirCacheEntry)
   (org.eclipse.jgit.revwalk RevWalk RevCommit RevTree)
   (org.eclipse.jgit.revwalk.filter RevFilter)
   (org.eclipse.jgit.treewalk TreeWalk)
   (org.eclipse.jgit.treewalk.filter PathFilter )
   (org.eclipse.jgit.api Git AddCommand CommitCommand)
   (org.eclipse.jgit.api.errors RefNotAdvertisedException)
   (org.eclipse.jgit.internal.storage.file FileRepository)
   (org.eclipse.jgit.transport RefSpec RemoteRefUpdate RemoteRefUpdate$Status)))

(def-record git
  [git-handle
   git-directory])

(def commit-id realm/string)

(defn- create-temp-dir! [name]
  (.toFile
   (Files/createTempDirectory
    name
    (make-array java.nio.file.attribute.FileAttribute 0))))

(defn clone!
  ([uri]
   (clone! (create-temp-dir! "git") uri))
  ([dir uri]
   (let [handle
         (-> (Git/cloneRepository)
             (.setDirectory dir)
             (.setURI uri)
             (.call))]
     (git git-handle handle
          git-directory dir))))

(defn- delete-files-recursively [f]
  (when (.isDirectory (io/file f))
    (doseq [f* (.listFiles (io/file f))]
      (delete-files-recursively f*)))
  (io/delete-file f :fail))

(defn kill! [git]
  (event-logger/log-event! :info (str "Deleting git directory: " (git-directory git)))
  (delete-files-recursively (git-directory git)))

(defn head [git & [branch]]
  (let [repo (.getRepository (git-handle git))]
    ;; TODO: make configurable `master`
    (when-let [object-id (.resolve repo (str
                                         "refs/heads/"
                                         (or branch "master")))]
      (org.eclipse.jgit.lib.ObjectId/toString object-id))))

(defn get!
  "Get contents of the given commit-id and filename as a string"
  [git commit-id filename]
  (let [repo (.getRepository (git-handle git))
        object-id (.resolve repo commit-id)
        rev-walk (RevWalk. repo)
        commit (.parseCommit rev-walk object-id)
        tree (.getTree commit)
        tree-walk (doto (TreeWalk. repo)
                    (.addTree tree)
                    (.setRecursive true)
                    (.setFilter (PathFilter/create filename)))]
    (if (.next tree-walk)
      (let [object-id (.getObjectId tree-walk 0)
            loader (.open repo object-id)]
        (String. (.getBytes loader) StandardCharsets/UTF_8))
      (throw (RuntimeException. "File not found in commit")))))

(defn checkout! [git commit-id]
  (.call
   (doto (.checkout (git-handle git))
     (.setName commit-id))))

(defn add! [git file-path]
  (.call
   (doto (.add (git-handle git))
     (.addFilepattern file-path))))

(defn commit! [git message]
  (.call
   (doto (.commit (git-handle git))
     (.setMessage message))))

(defn fetch! [git]
  (.call (.fetch (git-handle git))))

;; ---

(defn- commit-for-id [repo commit-id]
  (let [rev-walk (new RevWalk repo)
        commit (.parseCommit rev-walk commit-id)]
    commit))

(defn- string-for-path-in-tree [repo path tree]
  (let [reader (.newObjectReader repo)
        tw (TreeWalk. reader)
        filt (PathFilter/create path)]
    (.addTree tw tree)
    (.setFilter tw filt)

    (when (.next tw)
      (let [obj-id (.getObjectId tw 0)
            loader (.open reader obj-id)
            bytes (.getBytes loader)]
        (String. bytes StandardCharsets/UTF_8)))))

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

(defn merge-base
  "Returns a RevCommit (not an id)"
  [^Repository repo
   c1id
   c2id]
  (let [rev-walk (new RevWalk repo)]
    (.setRevFilter rev-walk RevFilter/MERGE_BASE)
    (.markStart rev-walk (commit-for-id repo c1id))
    (.markStart rev-walk (commit-for-id repo c2id))
    (.next rev-walk)))

(defn- merge-commit!
  "Return commit-id of resulting merge commit"
  [^Repository repo
   ^RevCommit base-commit
   ^RevCommit ours-commit
   ^RevCommit theirs-commit
   path
   merge-strings]

  (let [base-string (string-for-path-in-tree repo path (.getTree base-commit))
        ours-string (string-for-path-in-tree repo path (.getTree ours-commit))
        theirs-string (string-for-path-in-tree repo path (.getTree theirs-commit))
        result-string (merge-strings base-string ours-string theirs-string)]

    (let [blob-id (insert-string! repo result-string)
          tree-id (insert-tree-with-file! repo path blob-id)]

      (let [commit-builder (new CommitBuilder)]
        (.setTreeId commit-builder tree-id)
        (.setParentIds commit-builder ours-commit theirs-commit)
        (.setAuthor commit-builder (new org.eclipse.jgit.lib.PersonIdent "Speubot" "info@active-group.de"))
        (.setCommitter commit-builder (new org.eclipse.jgit.lib.PersonIdent "Speubot" "info@active-group.de"))
        (.setMessage commit-builder "Merge")

        (let [inserter (.newObjectInserter repo)
              commit-id (.insert inserter commit-builder)]
          (.flush inserter)
          commit-id)))))

(defn- set-master! [repo commit-id]
  (let [ref-update (.updateRef repo "refs/heads/master")]
    (.setNewObjectId ref-update commit-id)
    (.update ref-update)))

(defn predecessor? [repo c1id c2id]
  (let [rw (new RevWalk repo)]
    (.isMergedInto rw
                   (.parseCommit rw c1id)
                   (.parseCommit rw c2id))))

(defn pull! [git & [path merge-strings]]
  (let [repo (.getRepository (git-handle git))
        ours-commit-id (.resolve repo "refs/heads/master")
        _ (println "ours: " (pr-str ours-commit-id))
        theirs-commit-id (.resolve repo "refs/remotes/origin/master")
        _ (println "theirs: " (pr-str theirs-commit-id))
        base-commit (merge-base repo ours-commit-id theirs-commit-id)
        _ (println "base: " (pr-str base-commit))
        ours-commit (commit-for-id repo ours-commit-id)
        theirs-commit (commit-for-id repo theirs-commit-id)]

    (if (.equals ours-commit-id
                 theirs-commit-id)
      ;; nothing to do
      (do
        (println "pull!: nothing to do")
        ours-commit-id)

      ;; else
      (if (predecessor? repo ours-commit-id theirs-commit-id)
        ;; setting our master ref is sufficient
        (do
          (println "pull!: fast-forward")
          (set-master! repo theirs-commit-id)
          theirs-commit-id)

        ;; else
        (do
          (println "real merge")
          (let [merge-commit-id (merge-commit! repo base-commit ours-commit theirs-commit path merge-strings)]
            (set-master! repo merge-commit-id)
            merge-commit-id))))))

(defn push!
  "Returns a commit-id (String) when successful"
  [git]
  (try
    (let [refspec (RefSpec. "HEAD:refs/heads/master")
          push-results (.call
                        (doto (.push (git-handle git))
                          (.setRemote "origin")
                          (.setRefSpecs [refspec])))
          push-result (first push-results)
          upd (.getRemoteUpdate push-result
                                "refs/heads/master")]
      (when (or (= RemoteRefUpdate$Status/OK
                   (.getStatus upd))
                (= RemoteRefUpdate$Status/UP_TO_DATE
                   (.getStatus upd)))
        (.getName (.getNewObjectId upd))))
    (catch Exception e
      ;; TODO: handle exceptions
      nil)))

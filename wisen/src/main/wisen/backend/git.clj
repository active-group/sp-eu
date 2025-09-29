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

;; ---

(declare file-tree)

(def file realm/string)

(def filename realm/string)

(def folder
  (realm/map-of filename
                (realm/delay file-tree)))

(def file-tree
  (realm/union file folder))

(def commit-id realm/string)

;; ---

(defn git-directory [g]
  (.getAbsolutePath
   (.getDirectory
    (.getRepository g))))

(defn- get-string-for-id! [repo object-id]
  (String. (.getBytes (.open repo object-id))
           StandardCharsets/UTF_8))

(defn- get-tree-for-tree-id! [repo tree-id]
  (let [rw (RevWalk. repo)]
    (.parseTree rw tree-id)))

(defn- jgit-tree->file-tree [repo tree]
  (let [tw (doto (TreeWalk. repo)
             (.addTree tree)
             (.setRecursive false))]
    (loop [acc {}]
      (if (.next tw)
        (let [name (.getNameString tw)
              obj-id (.getObjectId tw 0)]
          (recur
           (assoc acc
                  name
                  (if (.isSubtree tw)
                    ;; subtree
                    (jgit-tree->file-tree
                     repo
                     (get-tree-for-tree-id! repo obj-id))
                    ;; else leaf
                    (get-string-for-id! repo obj-id)))))
        ;; else done
        acc))))

(defn- insert-string! [repo s]
  (let [inserter (.newObjectInserter repo)
        blob-id (.insert inserter Constants/OBJ_BLOB
                         (.getBytes s "UTF-8"))]
    (.flush inserter)
    blob-id))

(defn- folder->jgit-tree! [repo file-tree]
  (let [inserter (.newObjectInserter repo)
        formatter (new TreeFormatter)]

    (doseq [[name ft] file-tree]
      (if (string? ft)
        (let [blob-id (insert-string! repo ft)]
          (.append formatter name FileMode/REGULAR_FILE blob-id))
        ;; else folder
        (let [tree-id (folder->jgit-tree! repo ft)]
          (.append formatter name FileMode/TREE tree-id))))
    
    (.flush inserter)
    (.insert inserter formatter)))

(defn- create-temp-dir! [name]
  (.toFile
   (Files/createTempDirectory
    name
    (make-array java.nio.file.attribute.FileAttribute 0))))

(defn clone!
  "Returns an object that's used as a handle for subsequent operations"
  ([uri]
   (clone! (create-temp-dir! "git") uri))
  ([dir uri]
   (event-logger/log-event! :info (str "(clone! " uri ")"))
   (-> (Git/cloneRepository)
       (.setBare true)
       (.setDirectory dir)
       (.setURI uri)
       (.call))))

(defn- format-status [^Status status]
  (let [section (fn [title coll formatter]
                  (when (seq coll)
                    (str title
                         (apply str (map #(str "\n  " (formatter %)) coll))
                         "\n")))]
    (str
     (section "Added:" (.getAdded status) identity)
     (section "Changed:" (.getChanged status) identity)
     (section "Removed:" (.getRemoved status) identity)
     (section "Modified:" (.getModified status) identity)
     (section "Missing:" (.getMissing status) identity)
     (section "Untracked:" (.getUntracked status) identity)
     (section "Untracked folders:" (.getUntrackedFolders status) identity)
     (section "Conflicting (unmerged):" (.getConflicting status) identity)
     ;; optionally, include stage info per file
     (when (seq (.getConflictingStageState status))
       (str "Conflicting stage state:\n"
            (apply str
                   (for [[path stage] (.getConflictingStageState status)]
                     (str "  " path " -> " stage "\n")))))
     (when-not (.hasUncommittedChanges status)
       "Working tree clean\n"))))

(defn status! [git]
  (.call (.status git)))

(defn- delete-files-recursively [f]
  (when (.isDirectory (io/file f))
    (doseq [f* (.listFiles (io/file f))]
      (delete-files-recursively f*)))
  (io/delete-file f :fail))

#_(defn- log-status! [git]
  (event-logger/log-event! :info (str "Status for repository: " (git-directory git)))
  (event-logger/log-event! :info (format-status (status! git))))

(defn kill! [git]
  (event-logger/log-event! :info (str "Deleting git directory: " (git-directory git)))
  (delete-files-recursively (git-directory git)))

(defn head! [git & [branch]]
  (let [repo (.getRepository git)]
    ;; TODO: make configurable `master`
    (when-let [object-id (.resolve repo (str
                                         "refs/heads/"
                                         (or branch "master")))]
      (org.eclipse.jgit.lib.ObjectId/toString object-id))))

(defn get!
  "Returns a `file-tree` for the given string `commit-id`"
  [git commit-id]
  (let [repo (.getRepository git)]
    (jgit-tree->file-tree
     repo
     (get-tree-for-tree-id! repo
                            (.resolve repo commit-id)))))

(defn fetch! [git]
  (event-logger/log-event! :info "(fetch!)")
  (let [repo (.getRepository git)
        config (.getConfig repo)
        remote-config (org.eclipse.jgit.transport.RemoteConfig. config "origin")
        uri (.get (.getURIs remote-config) 0)
        transport (org.eclipse.jgit.transport.Transport/open repo uri)
        ref-spec (RefSpec. "refs/heads/master:refs/heads/origin/master")
        fetch-result (.fetch transport org.eclipse.jgit.lib.NullProgressMonitor/INSTANCE [ref-spec])]
    (.getName
     (.getObjectId
      (first
       (seq
        (.getAdvertisedRefs
         fetch-result)))))))

;; ---

(defn- commit-for-id [repo commit-id]
  (let [rev-walk (new RevWalk repo)
        commit (.parseCommit rev-walk (.resolve repo commit-id))]
    commit))

(defn commit!
  "Create a new commit on top of `parent-commit` with
  `file-tree`. Returns the resulting commit-id."
  [git folder message parent-commit-1 & [parent-commit-2]]
  (let [repo (.getRepository git)
        tree-id (folder->jgit-tree! repo folder)]

    (let [commit-builder (new CommitBuilder)]
      (.setTreeId commit-builder tree-id)
      (if parent-commit-2
        (.setParentIds commit-builder (.resolve repo parent-commit-1) (.resolve repo parent-commit-2))
        (.setParentId commit-builder (.resolve repo parent-commit-1)))
      (.setAuthor commit-builder (new org.eclipse.jgit.lib.PersonIdent "Speubot" "info@active-group.de"))
      (.setCommitter commit-builder (new org.eclipse.jgit.lib.PersonIdent "Speubot" "info@active-group.de"))
      (.setMessage commit-builder message)

      (let [inserter (.newObjectInserter repo)
            commit-id (.insert inserter commit-builder)]
        (.flush inserter)
        (.getName commit-id)))))

(defn merge-base
  "Returns a commit-id"
  [git c1id c2id]
  (let [repo (.getRepository git)
        rev-walk (new RevWalk repo)]
    (.setRevFilter rev-walk RevFilter/MERGE_BASE)
    (.markStart rev-walk (.parseCommit rev-walk (.resolve repo c1id)))
    (.markStart rev-walk (.parseCommit rev-walk (.resolve repo c2id)))
    (.getName
     (.next rev-walk))))

(defn merge-commit!
  "Return commit-id of resulting merge commit"
  ([git
    ours-commit
    theirs-commit
    merge-folders]
   (let [base-commit (merge-base git ours-commit theirs-commit)]
     (merge-commit! git base-commit ours-commit theirs-commit merge-folders)))

  ([git
    base-commit-id
    ours-commit-id
    theirs-commit-id
    merge-folders]

   (let [repo (.getRepository git)
         base-folder (jgit-tree->file-tree repo
                                           (.getTree (commit-for-id
                                                      repo
                                                      base-commit-id)))
         ours-folder (jgit-tree->file-tree repo
                                           (.getTree (commit-for-id
                                                      repo
                                                      ours-commit-id)))
         theirs-folder (jgit-tree->file-tree repo
                                             (.getTree (commit-for-id
                                                        repo
                                                        theirs-commit-id)))
         result-folder (merge-folders base-folder ours-folder theirs-folder)]

     (commit! git result-folder "merge" ours-commit-id theirs-commit-id))))

(defn- predecessor? [repo c1id c2id]
  (let [rw (new RevWalk repo)]
    (.isMergedInto rw
                   (.parseCommit rw (.resolve repo c1id))
                   (.parseCommit rw (.resolve repo c2id)))))

(defn join! [git commit-1 commit-2 merge-folders]
  (let [repo (.getRepository git)]
    (cond
      (predecessor? repo commit-1 commit-2)
      commit-2

      (predecessor? repo commit-2 commit-1)
      commit-1

      :else
      (merge-commit! git commit-1 commit-2 merge-folders))))

(defn update-master-ref! [git
                          from-commit
                          to-commit]
  (let [repo (.getRepository git)
        upd (.updateRef repo "refs/heads/master")]
    (.setExpectedOldObjectId upd (.resolve repo from-commit))
    (.setNewObjectId upd (.resolve repo to-commit))
    (.update upd)))

(defn update-successful? [ref-result]
  (or
   (.equals ref-result RefUpdate$Result/FAST_FORWARD)
   (.equals ref-result RefUpdate$Result/NEW)
   (.equals ref-result RefUpdate$Result/FORCED)
   (.equals ref-result RefUpdate$Result/NO_CHANGE)
   (.equals ref-result RefUpdate$Result/RENAMED)))

(defn join-master! [git to-commit merge-folders]
  (let [hd (head! git)
        ref-result (update-master-ref! git hd to-commit)]
    (if (update-successful? ref-result)
      to-commit
      (let [join-commit (join! git hd to-commit merge-folders)]
        ;; try again with join commit
        (recur git join-commit merge-folders)))))

(defn push!
  "Returns a commit-id (String) when successful"
  [git]
  (event-logger/log-event! :info "(push!)")
  (try
    (let [refspec (RefSpec. "HEAD:refs/heads/master")
          push-results (.call
                        (doto (.push git)
                          (.setRemote "origin")
                          (.setRefSpecs [refspec])))
          push-result (first push-results)
          upd (.getRemoteUpdate push-result
                                "refs/heads/master")]
      (if (or (= RemoteRefUpdate$Status/OK
                 (.getStatus upd))
              (= RemoteRefUpdate$Status/UP_TO_DATE
                 (.getStatus upd)))
        (.getName (.getNewObjectId upd))
        ;; else failed
        (do
          (event-logger/log-event! :error (str "push! failed: " (pr-str (.getStatus upd)) " -- " (pr-str upd)))
          nil)))
    (catch Exception e
      (event-logger/log-event! :error (str "push! failed: " (pr-str e)))
      nil)))

(defn sync-master! [git merge-folders]
  (or (push! git)
      (let [origin-head (fetch! git)
            _next-hd (join-master! git
                                   origin-head
                                   merge-folders)]
        (recur git merge-folders))))

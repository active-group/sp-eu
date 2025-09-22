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
   (org.eclipse.jgit.revwalk RevWalk)
   (org.eclipse.jgit.treewalk TreeWalk)
   (org.eclipse.jgit.treewalk.filter PathFilter)
   (org.eclipse.jgit.api Git AddCommand CommitCommand)
   (org.eclipse.jgit.merge MergeStrategy Merger)
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

#_(defn- make-merger [repo merge-strings]
  (proxy [ResolveMerger] [repo]
    (mergeContent
      [base ours theirs attributes]
      (let [base-s (when base (.getString base 0 ))])
      )))

#_(defn- make-merge-strategy [merge-strings]
  (proxy [MergeStrategy] []
    (getName [] "customMergeStrategy")
    (newMerger [repo] (make-merger repo merge-strings))))

#_(defn merge!
  "merge-strings : String x String x String -> String, run for each
  file"
  [git other-branch & [merge-strings]]
  ;; TODO: use custom Merger and MergeStrategy
  (let [repo (.getRepository (git-handle git))
        object-id (.resolve repo other-branch)])
  (doto (.merge (git-handle git))
    (.setStrategy jsonldMergeStrategy)
    (.call)))

(defn pull! [git & [merge-strings]]
  (let [merge-strategy (if merge-strings
                         MergeStrategy/OURS
                         #_(make-merge-strategy
                          (.getRepository (git-handle git))
                          merge-strings)
                         MergeStrategy/OURS)]
    (.call
     (doto (.pull (git-handle git))
       (.setRemote "origin")
       (.setRemoteBranchName "master")
       (.setRebase false)
       (.setStrategy merge-strategy)))))

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

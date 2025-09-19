(ns wisen.backend.git
  (:require [active.data.record :refer [def-record]]
            [clojure.java.io :as io]
            [wisen.common.prefix :refer [prefix]]
            [active.data.realm :as realm])
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

(defn kill! [git]
  ::TODO)

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

(def g
 (clone! "file:///Users/markusschlegel/Desktop/tmp/repo"))

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

#_#_#_#_#_#_#_#_#_#_#_#_#_(def gi (clone! "file:///Users/markusschlegel/Desktop/tmp/repo"))
(head gi)
;; => "e3f05a56d0bfc8b39228a8f7ff34ee6c4a3dc167"

(checkout! gi  "e3f05a56d0bfc8b39228a8f7ff34ee6c4a3dc167")
(def path (str (git-directory gi)
               "/" "model.json"))
path
(spit path "{    }")
(add! gi "model.json")
(commit! gi "update par")

(def rspec (RefSpec. "HEAD:refs/heads/master"))
(def res
  (push! gi))

res

(.getName
 (.getNewObjectId
  (.getRemoteUpdate (first res)
                    "refs/heads/master")))

(str path)
;; => "/var/folders/bc/pb0xvcwn0vd3xcgmplrvd2200000gn/T/git16079764626341900705/model.json"


#_(pull! gi)
;; => 

#_(def bare-directory "model.git")
#_(def working-directory "model.working")

#_(def ^:private git (atom nil))

#_(defn- post-receive-contents []
  (str
   "curl -X POST " (prefix) "/api/sync"))

#_(defn- setup-bare! []
  (when-not (.exists (io/file bare-directory))
    ;; git init
    (-> (Git/init)
        (.setBare true)
        (.setDirectory (java.io.File. bare-directory))
        (.call))

    ;; post-receive hook
    (let [post-receive-file (io/file (str bare-directory "/" "hooks/post-receive"))]
      (spit post-receive-file
            (post-receive-contents))
      (.setExecutable post-receive-file true true))))

#_(declare add! commit! push!)

#_(defn clone! [directory-string repo-uri-string]
  (-> (Git/cloneRepository)
      (.setDirectory (java.io.File. directory-string))
      (.setURI repo-uri-string)
      (.call)))

#_(defn- setup-clone! []
  ;; git clone
  (when-not (.exists (io/file working-directory))
    (-> (Git/cloneRepository)
        (.setDirectory (java.io.File. working-directory))
        (.setURI (str "file://" (.getAbsolutePath (java.io.File. bare-directory))))
        (.call))

    ;; add empty model.json file if not yet exists
    (let [model-json-file (io/file (str working-directory "/" "model.json"))]
      (when-not (.exists model-json-file)
        (spit model-json-file "{}\n")
        (add! "model.json")
        (commit! "Initial empty model.json")
        (push!)))))


(ns wisen.backend.git
  (:require [clojure.java.io :as io])
  (:import
   (java.io File)
   (org.eclipse.jgit.api Git AddCommand CommitCommand)
   (org.eclipse.jgit.api.errors RefNotAdvertisedException)
   (org.eclipse.jgit.internal.storage.file FileRepository)
   (org.eclipse.jgit.transport RefSpec)))

(def bare-directory "model.git")
(def working-directory "model.working")

(def ^:private git (atom nil))

(defn- setup-bare! []
  (when-not (.exists (io/file bare-directory))
    ;; git init
    (-> (Git/init)
        (.setBare true)
        (.setDirectory (java.io.File. bare-directory))
        (.call))

    ;; post-receive hook
    (spit (str bare-directory "/" "hooks/post-receive")
          ;; TODO: parameterize over base url
          "curl -X POST http://localhost:4321/api/sync")))

(defn- setup-clone! []
  ;; git clone
  (when-not (.exists (io/file working-directory))
    (-> (Git/cloneRepository)
        (.setDirectory (java.io.File. working-directory))
        (.setURI (str "file://" (.getAbsolutePath (java.io.File. bare-directory))))
        (.call))))

(defn setup! []
  (setup-bare!)
  (setup-clone!))

(defn- ensure! [f]
  (let [g @git]
    (if (nil? g)
      (let [g* (Git/open (java.io.File. working-directory))]
        (reset! git g*)
        (f g*))
      ;; else
      (f g))))

(defn pull! []
  (ensure!
   (fn [g]
     (try
       (let [cmd (.pull g)]
         (.setRemote cmd "origin")
         (.setRemoteBranchName cmd "master")
         (.call cmd))
       (catch RefNotAdvertisedException e
         ;; happens initially when bare repo doesn't yet contain any
         ;; commits
         )))))

(defn add! [filename]
  (ensure!
   (fn [g]
     (let [cmd (.add g)]
       (.addFilepattern cmd filename)
       (.call cmd)))))

(defn commit! [message]
  (ensure!
   (fn [g]
     (let [cmd (.commit g)]
       (.setMessage cmd message)
       (.call cmd)))))

(defn push! []
  (ensure!
   (fn [g]
     (let [cmd (.push g)]
       (.setRemote cmd "origin")
       (.setRefSpecs
        cmd
        [(RefSpec.
          "refs/heads/master:refs/heads/master")])
       (.call cmd)))))

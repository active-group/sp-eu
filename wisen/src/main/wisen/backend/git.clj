(ns wisen.backend.git
  (:import
   (java.io File)
   (org.eclipse.jgit.api Git AddCommand CommitCommand)))

(def directory "user-data")

(def ^:private git (atom nil))

(defn- ensure-git! [f]
  (let [g @git]
    (if (nil? g)
      (let [g* (-> (Git/init)
                   (.setDirectory (java.io.File. directory))
                   (.call))]
        (reset! git g*)
        (f g*))
      ;; else
      (f g))))

(defn add! [filename]
  (ensure-git!
   (fn [g]
     (let [cmd (.add g)]
       (.addFilepattern cmd filename)
       (.call cmd)))))

(defn commit! [message]
  (ensure-git!
   (fn [g]
     (let [cmd (.commit g)]
       (.setMessage cmd message)
       (.call cmd)))))

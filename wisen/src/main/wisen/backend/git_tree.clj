(ns wisen.backend.git-tree
  (:refer-clojure :exclude [get assoc dissoc update])
  (:require [active.data.record :refer [def-record is-a?]]
            [active.data.realm :as realm]
            [clojure.java.io :as io])
  (:import (org.eclipse.jgit.treewalk TreeWalk)
           (org.eclipse.jgit.revwalk RevWalk)
           (java.nio.charset StandardCharsets)
           (org.eclipse.jgit.lib TreeFormatter Constants FileMode)))

(declare file-tree)

(def-record file
  ;; an empty object-id means: new, not seen before
  [file-object-id :- (realm/optional realm/string)
   file-string :- realm/string])

(def filename realm/string)

(def-record folder
  ;; an empty object-id means: new, not seen before
  [folder-object-id :- (realm/optional realm/string)
   folder-contents :- (realm/map-of filename
                                    (realm/delay file-tree))])

(def file-tree
  (realm/union file folder))

;; --- API ---

(defn make-file
  ([s]
   (file file-string s))
  ([object-id s]
   (file file-object-id object-id
         file-string s)))

(defn file? [x]
  (is-a? file x))

(defn make-folder
  ([contents]
   (folder folder-contents contents))
  ([object-id contents]
   (folder folder-object-id object-id
           folder-contents contents)))

(defn folder? [x]
  (is-a? folder x))

(defn get [ft fname]
  (clojure.core/get (folder-contents ft)
                    fname))

(defn assoc [ft fname fval]
  (folder
   folder-object-id nil
   folder-contents (clojure.core/assoc (folder-contents ft)
                                       fname
                                       fval)))

(defn dissoc [ft fname]
  (folder
   folder-object-id nil
   folder-contents (clojure.core/dissoc (folder-contents ft)
                                        fname)))

(defn update [ft fname f & args]
  (folder
   folder-object-id nil
   folder-contents (apply
                    clojure.core/update
                    (folder-contents ft)
                    fname
                    f args)))

(defn empty-folder [& [object-id]]
  (folder folder-object-id object-id
          folder-contents {}))

(defn object-id [ft]
  (cond
    (file? ft)
    (file-object-id ft)

    (folder? ft)
    (folder-object-id ft)))

(defn changed? [ft]
  (nil? (object-id ft)))

;; --- Conversions ---

(defn- get-tree-for-tree-id!
  "result : RevTree"
  [repo tree-id]
  (let [rw (RevWalk. repo)]
    (.parseTree rw tree-id)))

(defn- get-string-for-id! [repo object-id]
  (String. (.getBytes (.open repo object-id))
           StandardCharsets/UTF_8))

(defn from-jgit [repo tree]
  (let [tw (doto (TreeWalk. repo)
             (.addTree tree)
             (.setRecursive false))]
    (loop [acc {}]
      (if (.next tw)
        (let [name (.getNameString tw)
              obj-id (.getObjectId tw 0)]
          (recur
           (clojure.core/assoc acc
                               name
                               (if (.isSubtree tw)
                                 ;; subtree
                                 (from-jgit
                                  repo
                                  (get-tree-for-tree-id! repo obj-id))
                                 ;; else leaf
                                 (make-file
                                  (.getName obj-id)
                                  (get-string-for-id! repo obj-id))))))
        ;; else done
        (folder folder-object-id (.getName tree)
                folder-contents acc)))))

(defn- insert-string! [repo s]
  (let [inserter (.newObjectInserter repo)
        blob-id (.insert inserter Constants/OBJ_BLOB
                         (.getBytes s "UTF-8"))]
    (.flush inserter)
    blob-id))

(defn to-jgit [repo folder]
  (let [inserter (.newObjectInserter repo)
        formatter (new TreeFormatter)]

    (doseq [[name ft] (folder-contents folder)]
      (if (file? ft)
        (let [blob-id (or
                       (when-let [obj-id (file-object-id ft)]
                         (.resolve repo obj-id))
                       (insert-string! repo (file-string ft)))]
          (.append formatter name FileMode/REGULAR_FILE blob-id))
        ;; else folder
        (let [tree-id (or
                       (when-let [obj-id (folder-object-id ft)]
                         (.resolve repo obj-id))
                       (to-jgit repo
                                (folder-contents ft)))]
          (.append formatter name FileMode/TREE tree-id))))
    
    (.flush inserter)
    (.insert inserter formatter)))

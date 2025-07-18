(ns wisen.backend.indexer
  (:require [active.data.record :refer [def-record is-a?]]
            [wisen.backend.index :as index]
            [active.clojure.logger.event :as event-logger])
  (:import
   (java.util.concurrent.locks LockSupport)))

;; Indexer is a thread that runs indexing

;; --- public interface

;; starts an indexer thread and returns a handle
(declare run-new-indexer!)

;; takes a handle and a `task`
(declare add-task!)

;; currently there's only an "index everything anew" task
(def index-all-task ::index-all-task)

;; ---

(def-record handle
  [handle-tasks-atom
   handle-thread])

(defn- handle-task! [_task]
  (index/update-search-index!))

(defn get-next-task [tasks]
  (first tasks))

(defn get-residual-tasks [tasks]
  (rest tasks))

(defn run-new-indexer! [& [name]]
  (let [tasks-atom (atom [])
        thread (.start (.name (Thread/ofVirtual)
                              (or name "Indexer"))
                       (fn []
                         (event-logger/log-event! :info (str "Indexer "
                                                             (when name (str name " "))
                                                             "started"))
                         (loop []
                           ;; pop a task from tasks
                           (let [tasks @tasks-atom]
                             (if-let [task (get-next-task tasks)]
                               (do
                                 (when (compare-and-set! tasks-atom tasks (get-residual-tasks tasks))
                                   (event-logger/log-event! :info (str "Indexer "
                                                                       (when name (str name " "))
                                                                       "handling a task"))
                                   (handle-task! task)
                                   (event-logger/log-event! :info (str "Indexer "
                                                                       (when name (str name " "))
                                                                       "done handling a task")))
                                 (recur))
                               ;; go to sleep
                               (do
                                 (LockSupport/park)
                                 (recur))
                               )))))]
    (handle
     handle-tasks-atom tasks-atom
     handle-thread thread)))

(defn add-task! [handle task]
  (let [timestamp (.toEpochMilli (java.time.Instant/now))]
    (swap! (handle-tasks-atom handle)
           (fn [tasks]
             (sort-by first
                      (conj tasks
                            [timestamp task]))))
    (LockSupport/unpark (handle-thread handle))))

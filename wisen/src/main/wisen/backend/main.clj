(ns wisen.backend.main
  (:require [clojure.tools.cli :as cli]
            [wisen.backend.server :as server]
            [wisen.backend.git :as git]
            [active.clojure.logger.event :as event-logger]
            [nrepl.server :as nrepl]
            [wisen.backend.embedding :as embedding])
  (:gen-class))

(def opts
  [["-h" "--help" "Show this help about the command line arguments"]
   ["-c" "--config CONFIG" "Path to the configuration file (default: ./etc/config.edn)"
    :default "./etc/config.edn"]
   ["-r" "--nrepl PORT" "Start an nrepl server on port PORT"]])

(defn print-usage [opts-map]
  (println "Usage: wisen [options]")
  (println "Options:")
  (println (:summary opts-map)))

(defonce nrepl-server (atom nil))

(defmacro setup! [name & body]
  `(do
     (event-logger/log-event! :info (str "Setup: " ~name))
     (let [result# (do ~@body)]
       (event-logger/log-event! :info (str "Setup done: " ~name))
       result#)))

(defn register-embedding-cache-shutdown-hook! [embedding-cache-file]
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. ^Runnable
                             (fn [& args]
                               (event-logger/log-event! :info (str "Storing embedding cache: " embedding-cache-file))
                               (embedding/store!! embedding-cache-file)))))

(defn main [opts]
  (cond
    (:errors opts)
    (do
      (doall (map println (:errors opts)))
      (print-usage opts)
      (System/exit 1))

    (:help (:options opts))
    (do
      (print-usage opts)
      (System/exit 0))

    :else
    (let [options (:options opts)
          config-path (:config options)
          repo-uri (System/getenv "REPOSITORY")
          prefix (System/getenv "PREFIX")
          embedding-cache-file (or (System/getenv "EMBEDDINGCACHEFILE")
                                   "embeddings.cache")]

      (setup! (str "Embedding cache: " embedding-cache-file)
              (embedding/load!! embedding-cache-file))

      (setup! "Registering shutdown hook for embedding cache storage"
              (register-embedding-cache-shutdown-hook! embedding-cache-file))

      (setup! "Web server"
              (server/start! config-path repo-uri prefix))

      (when-let [port (:nrepl (:options opts))]
        (setup! "nrepl server"
                (reset! nrepl-server
                        (nrepl/start-server :port (Integer/parseInt port))))))))

(defn -main
  [& args]
  (let [min-log-level :info]
    (println "Setting minimum log level: " (pr-str min-log-level))
    (event-logger/set-global-log-events-config-from-map! {:min-level min-log-level}))
  (main (cli/parse-opts args opts)))

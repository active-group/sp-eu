(ns wisen.backend.main
  (:require [clojure.tools.cli :as cli]
            [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store]
            [wisen.backend.skolemizer :as skolemizer]
            [wisen.backend.git :as git]
            [active.clojure.logger.event :as event-logger]
            [wisen.backend.indexer :as indexer]
            [nrepl.server :as nrepl])
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
          prefix (System/getenv "PREFIX")]

      (let [indexer (setup! "Lucene indexer"
                            (indexer/run-new-indexer!))]

        (setup! "Web server"
                (server/start! indexer config-path repo-uri prefix)))

      (when-let [port (:nrepl (:options opts))]
        (setup! "nrepl server"
                (reset! nrepl-server
                        (nrepl/start-server :port (Integer/parseInt port))))))))

(defn -main
  [& args]
  (main (cli/parse-opts args opts)))

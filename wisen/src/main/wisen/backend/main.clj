(ns wisen.backend.main
  (:require [clojure.tools.cli :as cli]
            [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store]
            [wisen.backend.skolemizer :as skolemizer]
            [wisen.backend.importer :as importer]
            [nrepl.server :as nrepl])
  (:gen-class))

(def opts
  [["-h" "--help" "Show this help about the command line arguments"]
   ["-c" "--config CONFIG" "Path to the configuration file (default: ./etc/config.edn)"
    :default "./etc/config.edn"]
   ["-r" "--nrepl PORT" "Start an nrepl server on port PORT"]
   ["-s" "--skolemize FILE" "Skolemize a given JSON-LD file"]
   ["-i" "--import FILE" "Import a given JSON-LD file. Contents must be skolemized."]])

(defn print-usage [opts-map]
  (println "Usage: wisen [options]")
  (println "Options:")
  (println (:summary opts-map)))

(defonce nrepl-server (atom nil))

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

    (string? (:skolemize (:options opts)))
    (let [path (:skolemize (:options opts))]
      (skolemizer/skolemize-file path))

    (string? (:import (:options opts)))
    (let [path (:import (:options opts))]
      (importer/import-file path))

    :else
    (let [options (:options opts)
          config-path (:config options)]

      (triple-store/setup!)
      (server/start! config-path)

      (when-let [port (:nrepl (:options opts))]
        (reset! nrepl-server
                (nrepl/start-server :port (Integer/parseInt port)))))))

(defn -main
  [& args]
  (main (cli/parse-opts args opts)))

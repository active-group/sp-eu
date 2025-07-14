(ns wisen.backend.main
  (:require [clojure.tools.cli :as cli]
            [wisen.backend.server :as server]
            [wisen.backend.triple-store :as triple-store]
            [wisen.backend.skolemizer :as skolemizer])
  (:gen-class))

(def opts
  [["-h" "--help" "Show this help about the command line arguments"]
   ["-c" "--config CONFIG" "Path to the configuration file (default: ./etc/config.edn)"
    :default "./etc/config.edn"]
   ["-s" "--skolemize FILE" "Skolemize a given JSON-LD file"]])

(defn print-usage [opts-map]
  (println "Usage: wisen [options]")
  (println "Options:")
  (println (:summary opts-map)))

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

    :else
    (let [options (:options opts)
          config-path (:config options)]
      (triple-store/setup!)
      (server/start! config-path))))

(defn -main
  [& args]
  (main (cli/parse-opts args opts)))

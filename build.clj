(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'wisen/wisen)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def output-dir "public/js")
(def uber-dir "target/uber")
(def uber-file (str uber-dir
                    (format "/%s-%s-standalone.jar" (name lib) version)))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"
                                   :aliases [:backend]})))

(def cljs-basis (delay (b/create-basis {:project "deps.edn"
                                        :aliases [:shadow-cljs]})))

(defn clean [_]
  (b/delete {:path class-dir})
  (b/delete {:path output-dir})
  (b/delete {:path uber-dir})
  (b/delete {:path ".shadow-cljs"}))

(defn- process
  "Start a sub-process with `args` (like `clojure.tools.build.api/process`), but
  fail whenever its exit code signals failure."
  [args]
  (let [{exit :exit :as process-result} (b/process args)]
    (when (> exit 0)
      (println "Process failed:" process-result)
      (System/exit exit))))

(defn shadow-cljs [opts]
  (let [target (:target opts)
        task (:task opts)]
    (process
     (b/java-command {:basis @cljs-basis
                      :main 'clojure.main
                      :main-args ["-m"
                                  "shadow.cljs.devtools.cli"
                                  task
                                  target]}))))

(defn compile-clj [_]
  (b/compile-clj {:basis @basis
                  :ns-compile '[wisen.backend.main]
                  :class-dir class-dir}))

(defn copy-dirs [_]
  (b/copy-dir {:src-dirs ["src" "public" "resources"]
               :target-dir class-dir}))

(defn make-uber [_]
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'wisen.backend.main}))

(defn uber [_]
  (clean nil)
  (shadow-cljs {:task "release" :target "frontend"})
  (copy-dirs nil)
  (compile-clj nil)
  (make-uber nil))

(defn test-cljs [_]
  (shadow-cljs {:task "compile" :target "karma-test"})
  (process {:command-args ["npx" "karma" "start" "--single-run"]}))

(defn test-clj [_]
  (process {:command-args ["clojure" "-M:test:backend"]
            :dir "."
            :out :inherit
            :err :inherit}))

(defn cljs-watch [_]
  (shadow-cljs {:task "watch" :target "frontend"}))

(defn cljs-watch-test [_]
  (shadow-cljs {:task "watch" :target "test"}))

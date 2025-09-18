(ns wisen.common.prefix)

(defn prefix []
  #?(:clj (or (System/getenv "PREFIX") "http://localhost:4321")
     :cljs (.-prefix js/window)))

#?(:clj
   (def set-prefix-code
     (str "window.prefix = " (pr-str (prefix)))))

(defn resource [resource-id]
  (str (prefix) "/resource/" resource-id))

(defn resource-prefix []
  (str (prefix) "/resource/"))

(ns wisen.common.urn)

(defn urn? [x]
  (clojure.string/starts-with? x "urn:"))

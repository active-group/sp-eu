(ns wisen.common.lang)

(defmacro define-text [?name & ?pairs]
  (let [prs (partition 2 ?pairs)
        m (reduce (fn [acc [k v]]
                    (assoc acc k v))
                  {}
                  prs)]
    `(defn ~?name [lang#]
       (or
        (get ~m lang#)
        (get ~m ~en)
        (str '~?name)))))

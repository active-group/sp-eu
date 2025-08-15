(ns wisen.common.lang)

(defmacro text [& ?args]
  (let [[pairs dflt]
        (if (even? (count ?args))
          [(partition 2 ?args)
           "<translation missing>"]
          [(partition 2 (drop-last ?args))
           (last ?args)])

        m
        (reduce (fn [acc [k v]]
                  (assoc acc k v))
                {}
                pairs)]
    `(fn [lang#]
       (get ~m lang# ~dflt))))

(defmacro define-text [?name & ?pairs]
  (let [prs (partition 2 ?pairs)
        m (reduce (fn [acc [k v]]
                    (assoc acc k v))
                  {}
                  prs)]
    `(defn ~?name [lang#]
       (or
        (get ~m lang#)
        (str '~?name)))))

(defmacro define-text-function [?name ?args-vec & ?pairs]
  (let [prs (partition 2 ?pairs)
        m (reduce (fn [acc [k v]]
                    (assoc acc k `(fn [~@?args-vec]
                                    ~v)))
                  {}
                  prs)]
    `(defn ~?name [lang# & args#]
       (or
        (apply (get ~m lang#) args#)
        (str '~?name)))))

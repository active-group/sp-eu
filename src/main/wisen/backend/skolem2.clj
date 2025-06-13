(ns wisen.backend.skolem2
  (:require [wisen.common.change-api :as change-api]
            [wisen.common.prefix :as prefix]))

(letfn [(get-or-fail [m k]
          (let [v (get m k)]
            (if v
              v
              (assert false "get-or-fail failed"))))]

  (defn- skolemize-statement* [stmt existential->uri]
    (let [s (change-api/statement-subject stmt)
          p (change-api/statement-predicate stmt)
          o (change-api/statement-object stmt)]

      (change-api/make-statement
       (if (change-api/existential? s)
         (get-or-fail existential->uri s)
         s)
       (if (change-api/existential? p)
         (get-or-fail existential->uri p)
         p)
       (if (change-api/existential? o)
         (get-or-fail existential->uri o)
         o)))))

(declare skolemize-changeset*)

(defn skolemize-change [change existential->uri]
  (cond
    (change-api/add? change)
    [(change-api/make-add
      (skolemize-statement* (change-api/add-statement change)
                            existential->uri))]

    (change-api/delete? change)
    [(change-api/make-delete
      (skolemize-statement* (change-api/delete-statement change)
                            existential->uri))]

    (change-api/with-blank-node? change)
    (skolemize-changeset* (change-api/with-blank-node-changes change)
                          (assoc existential->uri
                                 (change-api/with-blank-node-existential change)
                                 (prefix/resource (str (random-uuid)))))))

(defn skolemize-changeset* [changes existential->uri]
  (reduce (fn [changes* change]
            (concat changes* (skolemize-change change existential->uri)))
          []
          changes))

(defn skolemize-changeset [changes]
  (skolemize-changeset* changes {}))

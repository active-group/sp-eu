(ns wisen.backend.skolem
  (:require [wisen.common.prefix :as prefix])
  (:import
   (org.apache.jena.rdf.model ResourceFactory ModelFactory)))

(defn- uri-for-bnode [prefix bnode]
  (prefix/resource (.getLabelString (.getId bnode))))

(defn- skolemize-node [prefix node]
  (if (.isAnon node)
    [(ResourceFactory/createResource (uri-for-bnode prefix node)) true]
    [node false]))

(defn- skolemize-statement [model prefix stmt]
  (let [subject (.getSubject stmt)
        [subject* subject-changed?] (skolemize-node prefix subject)
        predicate (.getPredicate stmt)
        object (.getObject stmt)
        [object* object-changed?] (skolemize-node prefix object)]
    (if (or subject-changed?
            object-changed?)
      [(.createStatement model subject* predicate object*) true]
      [stmt false])))

(defn skolemize-model [model prefix]
  (let [stmts (iterator-seq (.listStatements model))
        [stmts* changed?] (reduce (fn [[stmts changed?] stmt]
                                    (let [[stmt* changed?*] (skolemize-statement model prefix stmt)]
                                      [(conj stmts stmt*)
                                       (or changed? changed?*)]))
                                  [[] false]
                                  stmts)]
    (if changed?
      [(.add (ModelFactory/createDefaultModel) stmts*) true]
      [model false])))

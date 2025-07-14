(ns wisen.backend.skolem
  (:import
   (org.apache.jena.rdf.model ResourceFactory ModelFactory)))

(defn- uri-for-bnode [prefix bnode]
  (str prefix (.getLabelString (.getId bnode))))

(defn- skolemize-node [prefix node]
  (if (.isAnon node)
    (ResourceFactory/createResource (uri-for-bnode prefix node))
    node))

(defn- skolemize-statement [model prefix stmt]
  (let [subject (.getSubject stmt)
        subject* (skolemize-node prefix subject)
        predicate (.getPredicate stmt)
        object (.getObject stmt)
        object* (skolemize-node prefix object)]
    (.createStatement model subject* predicate object*)))

(defn skolemize-model [model prefix]
  (let [stmts (iterator-seq (.listStatements model))
        stmts* (map (fn [stmt]
                      (skolemize-statement model prefix stmt))
                    stmts)
        model* (ModelFactory/createDefaultModel)]
    (.add model* stmts*)
    model*))

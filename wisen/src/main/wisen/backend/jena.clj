(ns wisen.backend.jena
  (:require [wisen.backend.jsonld]
            [wisen.common.change-api :as change-api])
  (:import
   (java.io File)
   (org.apache.jena.rdf.model Model ModelFactory)
   (org.apache.jena.riot RDFParser Lang)
   (org.apache.jena.datatypes.xsd XSDDatatype)))

;; TODO: skolemization when?

(defn string->model [s]
  (let [model (ModelFactory/createDefaultModel)]
    (doto (RDFParser/create)
      (.source (java.io.StringReader. s))
      (.lang Lang/JSONLD11)
      (.parse model))
    model))

(defn model->string [model]
  (with-out-str
    (.write model *out* "JSONLD")))

(defn unwrap [node]
  (cond
    (.isURIResource node)
    (.getURI (.asResource node))

    (.isLiteral node)
    (let [lit (.asLiteral node)
          type (.getDatatype lit)]
      (cond
        (instance? org.apache.jena.datatypes.xsd.impl.XSDBaseStringType type)
        (change-api/make-literal-string (.getLexicalForm lit))

        (instance? org.apache.jena.datatypes.xsd.impl.XSDBaseNumericType type)
        (change-api/make-literal-decimal (.getLexicalForm lit))

        (.equals type XSDDatatype/XSDboolean)
        (change-api/make-literal-boolean (.getLexicalForm lit))

        (.equals type XSDDatatype/XSDtime)
        (change-api/make-literal-time (.getLexicalForm lit))

        (.equals type XSDDatatype/XSDdate)
        (change-api/make-literal-date (.getLexicalForm lit))

        :else ;; fallback: String
        (change-api/make-literal-string (.getLexicalForm lit))))))

(defn- parse-statement [stmt]
  (change-api/make-statement
   (.getURI (.getSubject stmt))
   (.getURI (.getPredicate stmt))
   (unwrap (.getObject stmt))))

(defn changeset
  "Calculate the set of changes going from `from` model to `to` model"
  [from to]
  (let [not-in-from (.difference to from)
        not-in-to (.difference from to)]
    (concat
     (map (fn [stmt]
            (change-api/make-add
             (parse-statement stmt)))
          (iterator-seq
           (.listStatements not-in-from)))
     (map (fn [stmt]
            (change-api/make-delete
             (parse-statement stmt)))
          (iterator-seq
           (.listStatements not-in-to))))))

(defn- unwrap! [model obj]
  (cond
    (change-api/literal-string? obj)
    (.createLiteral model (change-api/literal-string-value obj))

    (change-api/literal-decimal? obj)
    (.createTypedLiteral model (change-api/literal-decimal-value obj) XSDDatatype/XSDdecimal)

    (change-api/literal-boolean? obj)
    (.createTypedLiteral model (change-api/literal-boolean-value obj) XSDDatatype/XSDboolean)

    (change-api/literal-time? obj)
    (.createTypedLiteral model (change-api/literal-time-value obj) XSDDatatype/XSDtime)

    (change-api/literal-date? obj)
    (.createTypedLiteral model (change-api/literal-date-value obj) XSDDatatype/XSDdate)

    (change-api/uri? obj)
    (.createResource model (change-api/uri-value obj))))

(defn add-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (unwrap! model obj)]
    (.add ^Model model s p o)))

(defn remove-statement! [^Model model stmt]
  (let [obj (change-api/statement-object stmt)
        s (.createResource model (change-api/statement-subject stmt))
        p (.createProperty model (change-api/statement-predicate stmt))
        o (unwrap! model obj)]
    (.remove ^Model model s p o)))

(defn apply-changeset! [model changeset]
  (loop [changeset* changeset]
    (if (empty? changeset*)
      model
      (let [change (first changeset*)]
        (cond
          (change-api/add? change)
          (add-statement! model (change-api/add-statement change))

          (change-api/delete? change)
          (remove-statement! model (change-api/delete-statement change)))
        (recur (rest changeset*))))))

(defn union [model-1 model-2]
  (.union model-1 model-2))

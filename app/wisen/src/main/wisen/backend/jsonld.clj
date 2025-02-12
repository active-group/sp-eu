(ns wisen.backend.jsonld
  (:import
   (org.apache.jena.riot RDFParser RDFParserBuilder Lang)))

(defn json-ld-string->model [s]
  (let [parser-builder (RDFParser/fromString s Lang/JSONLD11)]
    (.toModel parser-builder)))

(defn model->json-ld-string [model]
  (with-out-str
    (.write model *out* "JSON-LD")))

;; {"@id": "http://example.org/foo", "http://schema.org/name": "baasb"}
#_(json-ld-string->model "{\"@id\": \"http://example.org/foo\", \"http://schema.org/name\": \"baasb\"}")

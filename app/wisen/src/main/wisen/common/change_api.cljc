(ns wisen.common.change-api
  (:refer-clojure :exclude [uri?])
  (:require #?(:cljs [active.data.record :as record :refer-macros [def-record]])
            #?(:clj [active.data.record :as record :refer [def-record]])
            [active.data.realm :as realm]
            [active.data.translate.core :as translate]
            [active.data.translate.format :as format]
            [active.data.translate.formatter :as formatter]))

(def uri realm/string)

(defn make-uri [s]
  s)

(defn uri? [x]
  (string? x))

(defn uri-value [s]
  s)

;;

(def-record literal-string
  [literal-string-value :- realm/string])

(defn make-literal-string [s]
  (literal-string literal-string-value s))

(defn literal-string? [x]
  (record/is-a? literal-string x))

;;

(def-record literal-decimal
  [literal-decimal-value :- realm/string #_realm/decimal])

(defn make-literal-decimal [s]
  (literal-decimal literal-decimal-value s))

(defn literal-decimal? [x]
  (record/is-a? literal-decimal x))

;;

(def literal-or-uri (realm/union literal-string literal-decimal uri))

;;

(def-record statement
  [statement-subject :- uri
   statement-predicate :- uri
   statement-object :- literal-or-uri])

(defn make-statement [s p o]
  (statement statement-subject s
             statement-predicate p
             statement-object o))

(defn statement? [x]
  (record/is-a? statement x))

;;

(def-record delete [delete-statement :- statement])

(defn make-delete [s]
  (delete delete-statement s))

(defn delete? [x]
  (record/is-a? delete x))

;;

(def-record add [add-statement :- statement])

(defn make-add [s]
  (add add-statement s))

(defn add? [x]
  (record/is-a? add x))

;;

(def change (realm/union delete add))

;;

(def edn-format
  (format/format ::edn-format
                 {realm/string formatter/id
                  realm/number formatter/id
                  literal-string (formatter/record-map literal-string {literal-string-value :value})
                  literal-decimal (formatter/record-map literal-decimal {literal-decimal-value :value})
                  literal-or-uri (formatter/tagged-union-tuple {"literal-string" literal-string
                                                                "literal-decimal" literal-decimal
                                                                "uri" realm/string})
                  statement (formatter/record-map statement {statement-subject :subject
                                                             statement-predicate :predicate
                                                             statement-object :object})
                  delete (formatter/record-map delete {delete-statement :statement})
                  add (formatter/record-map add {add-statement :statement})
                  change (formatter/tagged-union-tuple {"add" add
                                                        "delete" delete})
                  }))

(def change->edn
  (translate/translator-from change edn-format))

(def edn->change
  (translate/translator-to change edn-format))

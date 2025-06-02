(ns wisen.common.change-api
  (:refer-clojure :exclude [uri?])
  (:require #?(:cljs [active.data.record :as record :refer-macros [def-record]])
            #?(:clj [active.data.record :as record :refer [def-record]])
            [active.clojure.lens :as lens]
            [active.data.realm :as realm]
            [active.data.translate.core :as translate]
            [active.data.translate.format :as format]
            [active.data.translate.formatter :as formatter]))

(defn- tagged [tag l]
  (lens/xmap
   (fn [x]
     [tag (l x)])
   (fn [[_ x]]
     (l nil x))))

;;

(def uri realm/string)

(defn make-uri [s]
  s)

(defn uri? [x]
  (realm/contains? uri x))

(def uri<->edn
  lens/id)

(defn uri-value [s]
  s)

;;

(def existential realm/integer)

(defn make-existential [idx]
  idx)

(defn existential? [x]
  (realm/contains? existential x))

(def existential<->edn
  lens/id)

;;

(def uri-or-ex (realm/union uri existential))

(def uri-or-ex<->edn
  lens/id)

;;

(def-record literal-string
  [literal-string-value :- realm/string])

(defn make-literal-string [s]
  (literal-string literal-string-value s))

(defn literal-string? [x]
  (record/is-a? literal-string x))

(def literal-string<->edn
  (lens/xmap
   literal-string-value
   make-literal-string))

;;

(def-record literal-decimal
  [literal-decimal-value :- realm/string #_realm/decimal])

(defn make-literal-decimal [s]
  (literal-decimal literal-decimal-value s))

(defn literal-decimal? [x]
  (record/is-a? literal-decimal x))

(def literal-decimal<->edn
  (lens/xmap
   literal-decimal-value
   make-literal-decimal))

;;

(def-record literal-boolean
  [literal-boolean-value :- realm/string #_realm/boolean])

(defn make-literal-boolean [s]
  (literal-boolean literal-boolean-value s))

(defn literal-boolean? [x]
  (record/is-a? literal-boolean x))

(def literal-boolean<->edn
  (lens/xmap
   literal-boolean-value
   make-literal-boolean))

;;

(def-record literal-time
  [literal-time-value :- realm/string])

(defn make-literal-time [s]
  (literal-time literal-time-value s))

(defn literal-time? [x]
  (record/is-a? literal-time x))

(def literal-time<->edn
  (lens/xmap
   literal-time-value
   make-literal-time))

;;

(def-record literal-date
  [literal-date-value :- realm/string])

(defn make-literal-date [s]
  (literal-date literal-date-value s))

(defn literal-date? [x]
  (record/is-a? literal-date x))

(def literal-date<->edn
  (lens/xmap
   literal-date-value
   make-literal-date))

;;

(def leaf (realm/union
           literal-string
           literal-decimal
           literal-boolean
           literal-time
           literal-date
           uri-or-ex))

(def leaf<->edn
  (lens/union
   [literal-string? #(= "literal-string" (first %)) (tagged "literal-string" literal-string<->edn)]
   [literal-decimal? #(= "literal-decimal" (first %)) (tagged "literal-decimal" literal-decimal<->edn)]
   [literal-boolean? #(= "literal-boolean" (first %)) (tagged "literal-boolean" literal-boolean<->edn)]
   [literal-time? #(= "literal-time" (first %)) (tagged "literal-time" literal-time<->edn)]
   [literal-date? #(= "literal-date" (first %)) (tagged "literal-date" literal-date<->edn)]
   [uri? #(= "uri" (first %)) (tagged "uri" uri<->edn)]
   [existential? #(= "existential" (first %)) (tagged "existential" existential<->edn)]))

;;

(def-record statement
  [statement-subject :- uri-or-ex
   statement-predicate :- uri-or-ex
   statement-object :- leaf])

(defn make-statement [s p o]
  (statement statement-subject s
             statement-predicate p
             statement-object o))

(defn statement? [x]
  (record/is-a? statement x))

(def edn<->statement
  (lens/project
   {(lens/>> statement-subject uri-or-ex<->edn) :subject
    (lens/>> statement-predicate uri-or-ex<->edn) :predicate
    (lens/>> statement-object leaf<->edn) :object}
   (statement)))

(def statement<->edn (lens/invert edn<->statement {}))

;;

(def-record delete [delete-statement :- statement])

(defn make-delete [s]
  (delete delete-statement s))

(defn delete? [x]
  (record/is-a? delete x))

(def delete<->edn
  (lens/xmap
   (fn [x]
     (statement<->edn (delete-statement x)))
   (fn [x]
     (make-delete (statement<->edn nil x)))))

;;

(def-record add [add-statement :- statement])

(defn make-add [s]
  (add add-statement s))

(defn add? [x]
  (record/is-a? add x))

(def add<->edn
  (lens/xmap
   (fn [x]
     (statement<->edn (add-statement x)))
   (fn [x]
     (make-add (statement<->edn nil x)))))

;;

(declare change changeset<->edn)

(def-record with-blank-node
  [with-blank-node-existential :- existential
   with-blank-node-changes :- (realm/sequence-of
                               (realm/delay change))])

(defn make-with-blank-node [existential changes]
  (with-blank-node
    with-blank-node-existential existential
    with-blank-node-changes changes))

(defn with-blank-node? [x]
  (record/is-a? with-blank-node x))

(def edn<->with-blank-node
  (lens/project
   {(lens/>> with-blank-node-existential existential<->edn) :existential
    (lens/>> with-blank-node-changes (lens/defer #'changeset<->edn)) :changes}
   (with-blank-node)))

(def with-blank-node<->edn
  (lens/invert edn<->with-blank-node {}))

;;

(def change (realm/union delete add with-blank-node))

(def change<->edn
  (lens/union
   [add? #(= "add" (first %)) (tagged "add" add<->edn)]
   [delete? #(= "delete" (first %)) (tagged "delete" delete<->edn)]
   [with-blank-node? #(= "with-blank-node" (first %)) (tagged "with-blank-node" with-blank-node<->edn)]))

;;

(def changeset (realm/sequence-of change))

(def changeset<->edn
  (lens/xmap
   (fn [cs]
     (map change<->edn cs))
   (fn [edn]
     (map (partial change<->edn nil) edn))))

;; Convenience functions

(defn change->edn [chng]
  (change<->edn chng))

(defn edn->change [edn]
  (change<->edn nil edn))

(defn changeset->edn [chngs]
  (changeset<->edn chngs))

(defn edn->changeset [edn]
  (changeset<->edn nil edn))

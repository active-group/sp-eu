(ns wisen.frontend.change
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.common.change-api :as change-api]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.schema :as schema]
            [wisen.frontend.existential :as existential]
            [clojure.set :as set]))


;; A change describes a delta between two sets of trees with references, i.e. between two graphs

(def-record delete [delete-statement])

(defn make-delete [stmt]
  (delete delete-statement stmt))

(defn delete? [x]
  (is-a? delete x))

(def-record add [add-statement])

(defn make-add [stmt]
  (add add-statement stmt))

(defn add? [x]
  (is-a? add x))

(def-record with-blank-node
  [with-blank-node-existential :- existential/existential
   with-blank-node-changes])

(defn make-with-blank-node [existential changes]
  (with-blank-node
    with-blank-node-existential existential
    with-blank-node-changes changes))

(defn with-blank-node? [x]
  (is-a? with-blank-node x))

;; Statements

(def-record statement
  [statement-subject :- (realm/union existential/existential tree/URI)
   statement-predicate :- (realm/union existential/existential tree/URI)
   statement-object :- (realm/union tree/literal-string
                                    tree/literal-decimal
                                    tree/literal-boolean
                                    tree/literal-time
                                    tree/literal-date
                                    existential/existential
                                    tree/URI)])

(defn make-statement [s p o]
  (statement
   statement-subject s
   statement-predicate p
   statement-object o))

;; Change

(def change (realm/union add delete with-blank-node))

;; Changeset

(def changeset (realm/sequence-of change))

;; conversions

(defn statement->api [s]
  (let [subject (statement-subject s)
        predicate (statement-predicate s)
        object (statement-object s)]

    (change-api/make-statement
     (if (tree/uri? subject)
       (change-api/make-uri
        (tree/uri-string subject))
       (change-api/make-existential subject))

     (if (tree/uri? predicate)
       (change-api/make-uri
        (tree/uri-string predicate))
       (change-api/make-existential predicate))

     (let [obj (statement-object s)]
       (cond
         (tree/literal-string? obj)
         (change-api/make-literal-string
          (tree/literal-string-value obj))

         (tree/literal-decimal? obj)
         (change-api/make-literal-decimal
          (tree/literal-decimal-value obj))

         (tree/literal-boolean? obj)
         (change-api/make-literal-boolean
          (tree/literal-boolean-value obj))

         (existential/existential? obj)
         (change-api/make-existential obj)

         (tree/uri? obj)
         (change-api/make-uri
          (tree/uri-string obj)))))))

(declare changeset->api)

(defn change->api [ch]
  (cond
    (record/is-a? delete ch)
    (change-api/make-delete (statement->api
                             (delete-statement ch)))

    (record/is-a? add ch)
    (change-api/make-add (statement->api
                          (add-statement ch)))

    (record/is-a? with-blank-node ch)
    (change-api/make-with-blank-node
     (with-blank-node-existential ch)
     (changeset->api
      (with-blank-node-changes ch)))))

(defn changeset->api [cs]
  (map change->api cs))


;; GUI

(defn- nchanges [n p? changeset]
  (reduce
   (fn [n chng]
     (let [n* (if (p? chng)
                (inc n)
                n)]
       (cond
         (add? chng)
         n*

         (delete? chng)
         n*

         (with-blank-node? chng)
         (nchanges n* p? (with-blank-node-changes chng)))))
   n
   changeset))

(defn changeset-summary [schema changeset]
  (let [deletions (nchanges 0 delete? changeset)
        additions (nchanges 0 add? changeset)
        blank-creations (nchanges 0 with-blank-node? changeset)]
    (dom/div
     {:style {:display "flex"
              :gap "1em"}}
     (dom/div
      {:style {:color "green"}}
      (str additions)
      (when (> blank-creations 0)
        (str " + " blank-creations))
      " Additions")

     (dom/div
      {:style {:color "red"}}
      (str deletions)
      " Deletions"))))

(defn commit-changeset-request [changeset]
  (ajax/POST "/api/changes"
             {:body (pr-str {:changes
                             (change-api/changeset->edn
                              (changeset->api changeset))})
              :headers {:content-type "application/edn"}}))

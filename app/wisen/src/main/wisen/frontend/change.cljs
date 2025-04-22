(ns wisen.frontend.change
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.common.change-api :as change-api]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.schema :as schema]
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

(defn change-statement [x]
  (cond
    (delete? x)
    (delete-statement x)

    (add? x)
    (add-statement x)))

;; Statements

(def-record statement
  [statement-subject :- tree/URI
   statement-predicate :- tree/URI
   statement-object :- (realm/union tree/literal-string
                                    tree/literal-decimal
                                    tree/literal-boolean
                                    tree/URI)])

(defn make-statement [s p o]
  (statement
   statement-subject s
   statement-predicate p
   statement-object o))

;; conversions

(defn statement->api [s]
  (change-api/make-statement
   (change-api/make-uri
    (tree/uri-string
     (statement-subject s)))

   (change-api/make-uri
    (tree/uri-string
     (statement-predicate s)))

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

       (tree/uri? obj)
       (change-api/make-uri
        (tree/uri-string obj))))))

(defn change->api [ch]
  (cond
    (record/is-a? delete ch)
    (change-api/make-delete (statement->api
                             (delete-statement ch)))

    (record/is-a? add ch)
    (change-api/make-add (statement->api
                          (add-statement ch)))))


;; GUI

(defn changes-summary [schema changes]
  (let [deletions (filter delete? changes)
        additions (filter add? changes)]
    (dom/div
     {:style {:display "flex"
              :gap "1em"}}
     (dom/div
      {:style {:color "green"}}
      (str (count additions))
      " Additions")

     (dom/div
      {:style {:color "red"}}
      (str (count deletions))
      " Deletions"))))

(defn commit-changes-request [changes]
  (ajax/POST "/api/changes"
             {:body (pr-str {:changes
                             (map (comp change-api/change->edn
                                        change->api)
                                  changes)})
              :headers {:content-type "application/edn"}}))

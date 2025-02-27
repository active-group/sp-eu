(ns wisen.frontend.change
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.common.change-api :as change-api]
            [clojure.set :as set]))


;; A change describes a delta between two sets of trees with references, i.e. between two graphs

(def-record delete [delete-statement])

(defn make-delete [stmt]
  (delete delete-statement stmt))

(def-record add [add-statement])

(defn make-add [stmt]
  (add add-statement stmt))

;; Statements

(def-record statement
  [statement-subject :- tree/URI
   statement-predicate :- tree/URI
   statement-object :- (realm/union tree/literal-string
                                    tree/literal-decimal
                                    tree/URI)])

(defn- compare-object [o1 o2]
  (cond
    (and (tree/literal-string? o1)
         (tree/literal-string? o2))
    (compare (tree/literal-string-value o1)
             (tree/literal-string-value o2))

    (and (tree/literal-string? o1)
         (tree/uri? o2))
    -1

    (and (tree/literal-string? o1)
         (tree/literal-decimal? o2))
    -1

    (and (tree/uri? o1)
         (tree/literal-string? o2))
    1

    (and (tree/uri? o1)
         (tree/uri? o2))
    (compare (tree/uri-string o1)
             (tree/uri-string o2))

    (and (tree/uri? o1)
         (tree/literal-decimal? o2))
    1

    (and (tree/literal-decimal? o1)
         (tree/literal-string? o2))
    1

    (and (tree/literal-decimal? o1)
         (tree/uri? o2))
    -1

    (and (tree/literal-decimal? o1)
         (tree/literal-decimal? o2))
    (compare (tree/literal-decimal-value o1)
             (tree/literal-decimal-value o2))))

(defn compare-statement [s1 s2]
  (case (compare (statement-subject s1)
                 (statement-subject s2))
    1 1
    -1 -1
    0 (case (compare (statement-predicate s1)
                     (statement-predicate s2))
        1 1
        -1 -1
        0 (compare-object (statement-object s1)
                          (statement-object s2)))))

;; Algorithm

(defn delta-statements* [acc stmts1 stmts2]
  (cond
    (empty? stmts1)
    ;; add all stmts2
    (concat acc (map make-add stmts2))

    (empty? stmts2)
    ;; remove all stmts1
    (concat acc (map make-delete stmts1))

    :else
    (let [[stmt1 & stmts1*] stmts1
          [stmt2 & stmts2*] stmts2]
      (case (compare-statement stmt1 stmt2)
        -1
        ;; stmt1 is not in stmts2 -> deleted
        (delta-statements* (conj acc (make-delete stmt1))
                           stmts1*
                           stmts2)

        0
        ;; just continue
        (delta-statements* acc stmts1* stmts2*)

        1
        ;; stmt2 is not in stmts1 -> added
        (delta-statements* (conj acc (make-add stmt2))
                           stmts1
                           stmts2*)))))

(defn delta-statements [stmts1 stmts2]
  (delta-statements*
   #{}
   (sort compare-statement stmts1)
   (sort compare-statement stmts2)))

(defn- property->statements
  "subject :- tree/URI, prop :- tree/property"
  [subject prop]
  ;; TODO: maybe tailrec
  (let [mk-stmt (fn [object]
                  (statement statement-subject subject
                             statement-predicate (tree/property-predicate prop)
                             statement-object object))
        obj (tree/property-object prop)]
    (cond
      (tree/literal-string? obj)
      [(mk-stmt obj)]

      (tree/literal-decimal? obj)
      [(mk-stmt obj)]

      (tree/ref? obj)
      [(mk-stmt (tree/ref-uri obj))]

      (tree/node? obj)
      (conj (mapcat
             (fn [prop]
               (property->statements (tree/node-uri obj) prop))
             (tree/node-properties obj))
            (mk-stmt (tree/node-uri obj))))))

(defn tree-statements [t]
  (cond
    (tree/literal-string? t)
    (assert false "Not possible to turn literal string into statements")

    (tree/literal-decimal? t)
    (assert false "Not possible to turn literal decimal into statements")

    (tree/ref? t)
    (assert false "Not possible to turn ref into statements")

    (tree/node? t)
    (mapcat (fn [prop]
              (property->statements (tree/node-uri t) prop))
            (tree/node-properties t))))

(defn delta-tree [t1 t2]
  (delta-statements (tree-statements t1)
                    (tree-statements t2)))

(defn trees-statements [trees]
  (reduce (fn [acc tree]
            (set/union acc (set (tree-statements tree))))
          #{}
          trees))

(defn delta-trees [ts1 ts2]
  (delta-statements (trees-statements ts1)
                    (trees-statements ts2)))


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

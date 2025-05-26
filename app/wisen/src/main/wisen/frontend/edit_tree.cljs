(ns wisen.frontend.edit-tree
  "Turn a rooted tree (wisen.frontend.tree) into an edit tree, tracking changes"
  (:refer-clojure :exclude [exists?])
  (:require [wisen.frontend.tree :as tree]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [active.clojure.functions :as f]
            [wisen.frontend.change :as change]
            [wisen.frontend.existential :as existential]
            [wisen.frontend.util :as util]
            [wisen.frontend.forms :as forms]))

(def-record deleted
  [deleted-original-value])

(defn make-deleted [orig]
  (deleted deleted-original-value orig))

(defn deleted? [x]
  (is-a? deleted x))

(def-record added
  [added-result-value])

(defn mark-added [x]
  (added added-result-value x))

(defn added? [x]
  (is-a? added x))

(def-record maybe-changed
  [maybe-changed-original-value
   maybe-changed-result-value])

(defn make-maybe-changed [orig res]
  (maybe-changed maybe-changed-original-value orig
                 maybe-changed-result-value res))

(defn maybe-changed? [x]
  (is-a? maybe-changed x))

(defn- mark-same [x]
  (maybe-changed
   maybe-changed-original-value x
   maybe-changed-result-value x))


(defn can-delete? [x]
  (is-a? maybe-changed x))

(defn mark-deleted [x]
  (assert (is-a? maybe-changed x) "Can only mark deleted maybe-changed")
  (deleted
   deleted-original-value
   (maybe-changed-original-value x)))

(def marked (realm/union
             deleted
             added
             maybe-changed))

(defn marked-current [m]
  (cond
    (is-a? maybe-changed m)
    (maybe-changed-result-value m)

    (is-a? deleted m)
    (deleted-original-value m)

    (is-a? added m)
    (added-result-value m)))

(defn marked-result-value
  ([m]
   (cond
     (is-a? maybe-changed m)
     (maybe-changed-result-value m)

     (is-a? deleted m)
     nil

     (is-a? added m)
     (added-result-value m)))
  ([m v]
   (cond
     (is-a? maybe-changed m)
     (maybe-changed-result-value m v)

     (is-a? deleted m)
     m

     (is-a? added m)
     (added-result-value m v))))

;; ---

(declare edit-tree)

(def-record edit-node
  [edit-node-uri :- tree/URI
   edit-node-properties :- (realm/map-of tree/URI ; predicate
                                         (realm/sequence-of
                                          ;; payload: edit-tree
                                          marked))
   edit-node-focused? :- forms/selection-info])

(defn edit-node? [x]
  (is-a? edit-node x))

;;

(def-record many
  [many-edit-trees :- (realm/sequence-of (realm/delay edit-tree))
   many-focused? :- forms/selection-info])

(defn many? [x]
  (is-a? many x))

;;

(def-record exists
  [exists-k])

(defn exists? [x]
  (is-a? exists x))

;;

(def-record ref
  [ref-uri :- tree/URI
   ref-focused? :- forms/selection-info])

(defn make-ref
  ([uri]
   (make-ref uri forms/unselected))
  ([uri focused?]
   (ref ref-uri uri ref-focused? focused?)))

(defn ref? [x]
  (is-a? ref x))

;;

(def-record literal-string
  [literal-string-value :- realm/string
   literal-string-focused? :- forms/selection-info])

(defn make-literal-string
  ([s]
   (literal-string literal-string-value s
                   literal-string-focused? forms/unselected))
  ([s focused?]
   (literal-string literal-string-value s
                   literal-string-focused? focused?)))

(defn literal-string? [x]
  (is-a? literal-string x))

;;

(def-record literal-decimal
  [literal-decimal-value :- realm/string
   literal-decimal-focused? :- forms/selection-info])

(defn make-literal-decimal
  ([s]
   (literal-decimal literal-decimal-value s
                    literal-decimal-focused? forms/unselected))
  ([s focused?]
   (literal-decimal literal-decimal-value s
                    literal-decimal-focused? focused?)))

(defn literal-decimal? [x]
  (is-a? literal-decimal x))

;;

(def-record literal-boolean
  [literal-boolean-value :- realm/boolean
   literal-boolean-focused? :- forms/selection-info])

(defn make-literal-boolean
  ([s]
   (literal-boolean literal-boolean-value s
                    literal-boolean-focused? forms/unselected))
  ([s focused?]
   (literal-boolean literal-boolean-value s
                    literal-boolean-focused? focused?)))

(defn literal-boolean? [x]
  (is-a? literal-boolean x))

;;

(def-record literal-time
  [literal-time-value :- realm/string
   literal-time-focused? :- forms/selection-info])

(defn make-literal-time
  ([s]
   (literal-time literal-time-value s
                 literal-time-focused? forms/unselected))
  ([s focused?]
   (literal-time literal-time-value s
                 literal-time-focused? focused?)))

(defn literal-time? [x]
  (is-a? literal-time x))

;;

(def-record literal-date
  [literal-date-value :- realm/string
   literal-date-focused? :- forms/selection-info])

(defn make-literal-date
  ([s]
   (literal-date literal-date-value s
                 literal-date-focused? forms/unselected))
  ([s focused?]
   (literal-date literal-date-value s
                 literal-date-focused? focused?)))

(defn literal-date? [x]
  (is-a? literal-date x))

;;

(def edit-tree (realm/union
                ref
                literal-string
                literal-decimal
                literal-boolean
                literal-time
                literal-date
                exists
                edit-node))

(def edit-tree-focused?
  (util/cond-lens
   [ref ref-focused?]
   [literal-string literal-string-focused?]
   [literal-decimal literal-decimal-focused?]
   [literal-boolean literal-boolean-focused?]
   [literal-time literal-time-focused?]
   [literal-date literal-date-focused?]
   [many many-focused?]
   [edit-node edit-node-focused?]))

(defn focus
  "Heuristics to focus on an edit tree."
  [etree]
  (cond
    (ref? etree)
    (edit-tree-focused? etree (forms/selected))

    (literal-string? etree)
    (edit-tree-focused? etree (forms/make-selected))

    (literal-decimal? etree)
    (edit-tree-focused? etree (forms/make-selected))

    (literal-boolean? etree)
    (edit-tree-focused? etree (forms/selected))

    (literal-time? etree)
    (edit-tree-focused? etree (forms/selected))

    (literal-date? etree)
    (edit-tree-focused? etree (forms/selected))

    (is-a? many etree)
    (lens/overhaul etree (lens/>> many-edit-trees lens/first) focus)

    (is-a? edit-node etree)
    ;; focus the left-most neighbour
    (let [eprops (edit-node-properties etree)
          [eprops* focused?]
          (reduce (fn [[props* focused?] [pred metrees]]
                    (if focused?
                      [(assoc props* pred metrees) true]
                      (let [[metrees* focused?]
                            (reduce (fn [[metrees* focused?] metree]
                                      (if focused?
                                        [(conj metrees* metree) true]
                                        (cond
                                          (is-a? maybe-changed metree)
                                          [(conj metrees*
                                                 (lens/overhaul metree maybe-changed-result-value focus))
                                           true]

                                          (is-a? deleted metree)
                                          [(conj metrees* metree) false]

                                          (is-a? added metree)
                                          [(conj metrees*
                                                 (lens/overhaul metree added-result-value focus))
                                           true]))
                                      )
                                    []
                                    metrees)]
                        [(assoc props* pred metrees*) focused?])))
                  [{} false]
                  eprops)]
      (if focused?
        (edit-node-properties etree eprops*)
        ;; else focus on uri
        (edit-node-focused? etree (forms/selected))))))

(declare edit-tree-original)

(letfn [(k [make-edit-tree tree cns ex]
          (make-edit-tree
           ((tree/exists-k tree) ex)
           cns))]
  (defn- make-edit-tree [tree cns]
    (cond
      (tree/many? tree)
      (many
       many-edit-trees
       (map #(make-edit-tree % cns)
            (tree/many-trees tree)))

      (tree/exists? tree)
      (exists
       exists-k (f/partial k make-edit-tree tree cns))

      (tree/ref? tree)
      (make-ref (tree/ref-uri tree))

      (tree/literal-string? tree)
      (make-literal-string (tree/literal-string-value tree))

      (tree/literal-decimal? tree)
      (make-literal-decimal (tree/literal-decimal-value tree))

      (tree/literal-boolean? tree)
      (make-literal-boolean (tree/literal-boolean-value tree))

      (tree/literal-time? tree)
      (make-literal-time (tree/literal-time-value tree))

      (tree/literal-date? tree)
      (make-literal-date (tree/literal-date-value tree))

      (tree/node? tree)
      (edit-node
       edit-node-uri (tree/node-uri tree)
       edit-node-properties (reduce (fn [eprops prop]
                                      (update eprops
                                              (tree/property-predicate prop)
                                              conj
                                              (cns
                                               (make-edit-tree
                                                (tree/property-object prop)
                                                cns))))
                                    {}
                                    (tree/node-properties tree))))))

(defn make-added-edit-tree [tree]
  (make-edit-tree tree mark-added))

(defn make-same-edit-tree [tree]
  (make-edit-tree tree mark-same))

(declare edit-tree-result-tree)

(defn- edit-node-result-node [enode]
  (tree/make-node
   (edit-node-uri enode)
   (reduce (fn [props [pred metrees]]
             (let [trees (mapcat (fn [metree]
                                   (cond
                                     (is-a? maybe-changed metree)
                                     [(edit-tree-result-tree
                                       (maybe-changed-result-value metree))]

                                     (is-a? added metree)
                                     [(edit-tree-result-tree
                                       (added-result-value metree))]

                                     (is-a? deleted metree)
                                     []))
                                 metrees)]
               (if-not (empty? trees)
                 (concat props
                         (map (partial tree/make-property pred)
                              trees))
                 props)))
           []
           (edit-node-properties enode))))

(defn edit-tree-result-tree [etree]
  (cond
    (ref? etree)
    (tree/make-ref (ref-uri etree))

    (literal-string? etree)
    (tree/make-literal-string (literal-string-value etree))

    (literal-decimal? etree)
    (tree/make-literal-decimal (literal-decimal-value etree))

    (literal-boolean? etree)
    (tree/make-literal-boolean (literal-boolean-value etree))

    (literal-time? etree)
    (tree/make-literal-time (literal-time-value etree))

    (literal-date? etree)
    (tree/make-literal-date (literal-date-value etree))

    (is-a? many etree)
    (tree/make-many (map edit-tree-result-tree (many-edit-trees etree)))

    (is-a? edit-node etree)
    (edit-node-result-node etree)))

(defn set-edit-node-original [enode new-node]
  (assert (empty? (edit-node-properties enode)))
  (edit-node-properties enode
                        (edit-node-properties (make-edit-tree new-node mark-same))))

(defn edit-node-add-property [enode predicate object-tree]
  (lens/overhaul enode edit-node-properties
                 (fn [eprops]
                   (update eprops
                           predicate
                           conj
                           (mark-added (focus
                                        (make-added-edit-tree object-tree)))))))

(declare edit-tree-changeset*)

(defn- edit-tree-changeset** [constructor subject predicate etree exgen]
  (cond
    (ref? etree)
    [[(constructor (change/make-statement subject predicate (ref-uri etree)))]
     exgen]

    (literal-string? etree)
    [[(constructor (change/make-statement subject predicate (tree/make-literal-string (literal-string-value etree))))]
     exgen]

    (literal-decimal? etree)
    [[(constructor (change/make-statement subject predicate (tree/make-literal-decimal (literal-decimal-value etree))))]
     exgen]

    (literal-boolean? etree)
    [[(constructor (change/make-statement subject predicate (tree/make-literal-boolean (literal-boolean-value etree))))]
     exgen]

    (literal-time? etree)
    [[(constructor (change/make-statement subject predicate (tree/make-literal-time (literal-time-value etree))))]
     exgen]

    (literal-date? etree)
    [[(constructor (change/make-statement subject predicate (tree/make-literal-date (literal-date-value etree))))]
     exgen]

    (many? etree)
    (reduce (fn [[changeset exgen] etree]
              (let [[changeset* exgen*] (edit-tree-changeset** constructor subject predicate etree exgen)]
                [(concat changeset changeset*) exgen*]))
            [[] exgen]
            (many-edit-trees etree))

    (exists? etree)
    (existential/with-fresh-existential-2
      exgen
      (fn [ex exgen]
        (let [binder ex
              change* (constructor (change/make-statement subject predicate binder))
              [changes* exgen*] (edit-tree-changeset* ((exists-k etree) binder) exgen)]
          [[(change/make-with-blank-node binder (conj changes*
                                                      change*))]
           exgen*])))

    (is-a? edit-node etree)
    (let [uri (edit-node-uri etree)
          [changes* exgen*] (edit-tree-changeset* etree exgen)
          change* (constructor (change/make-statement subject predicate uri))]
      [(conj changes* change*) exgen*])))

(declare same? changed?)

(defn- edit-tree-changeset* [etree exgen]
  (cond
    (ref? etree)
    [[] exgen]

    (literal-string? etree)
    [[] exgen]

    (literal-decimal? etree)
    [[] exgen]

    (literal-boolean? etree)
    [[] exgen]

    (literal-time? etree)
    [[] exgen]

    (literal-date? etree)
    [[] exgen]

    (many? etree)
    (reduce (fn [[changeset exgen] etree]
              (let [[changeset* exgen*] (edit-tree-changeset* etree exgen)]
                [(concat changeset changeset*) exgen*]))
            [[] exgen]
            (many-edit-trees etree))

    (exists? etree)
    (existential/with-fresh-existential-2
      exgen
      (fn [ex exgen]
        (let [[changes* exgen*] (edit-tree-changeset* ((exists-k etree) ex) exgen)]
          [[(change/make-with-blank-node ex changes*)] exgen*])))

    (is-a? edit-node etree)
    (let [subject (edit-node-uri etree)]
      (reduce (fn [[changeset exgen] [predicate metrees]]
                (let [[changeset* exgen*]
                      (reduce (fn [[changeset exgen] metree]
                                (let [[changeset** exgen**]
                                      (cond
                                        (same? metree)
                                        (let [[cs1 exgen1] (edit-tree-changeset* (maybe-changed-original-value metree) exgen)
                                              [cs2 exgen2] (edit-tree-changeset* (maybe-changed-result-value metree) exgen1)]
                                          [(concat cs1 cs2) exgen2])

                                        (changed? metree)
                                        (let [[cs1 exgen1] (edit-tree-changeset**
                                                            change/make-delete
                                                            subject
                                                            predicate
                                                            (maybe-changed-original-value metree)
                                                            exgen)
                                              [cs2 exgen2] (edit-tree-changeset**
                                                            change/make-add
                                                            subject
                                                            predicate
                                                            (maybe-changed-result-value metree)
                                                            exgen1)]
                                          [(concat cs1 cs2) exgen2])

                                        (is-a? added metree)
                                        (edit-tree-changeset**
                                         change/make-add
                                         subject
                                         predicate
                                         (added-result-value metree)
                                         exgen)

                                        (is-a? deleted metree)
                                        (edit-tree-changeset**
                                         change/make-delete
                                         subject
                                         predicate
                                         (deleted-original-value metree)
                                         exgen))]

                                  [(concat changeset changeset**) exgen**]))

                              [[] exgen]
                              metrees)]

                  [(concat changeset changeset*) exgen*]))

              [[] exgen]

              (edit-node-properties etree)))))

(defn edit-tree-changeset [etree]
  (existential/with-exgen-2
    (fn [exgen]
      (first
       (edit-tree-changeset* etree exgen)))))

(defn edit-tree-commit-changes
  [etree]
  (cond
    (ref? etree)
    etree

    (literal-string? etree)
    etree

    (literal-decimal? etree)
    etree

    (literal-boolean? etree)
    etree

    (literal-time? etree)
    etree

    (literal-date? etree)
    etree

    (many? etree)
    (lens/overhaul etree many-edit-trees
                   #(map edit-tree-commit-changes %))

    (exists? etree)
    (exists
     exists-k
     (fn [ex]
       (edit-tree-commit-changes
        ((exists-k etree) ex))))

    (is-a? edit-node etree)
    (let [subject (edit-node-uri etree)]
      (edit-node-properties
       etree
       (reduce (fn [props* [predicate metrees]]
                 (assoc props* predicate
                        (mapcat
                         (fn [metree]
                           (cond
                             (is-a? maybe-changed metree)
                             [(mark-same (edit-tree-commit-changes
                                          (maybe-changed-result-value metree)))]

                             (is-a? added metree)
                             [(mark-same (edit-tree-commit-changes
                                          (added-result-value metree)))]

                             (is-a? deleted metree)
                             []))
                         metrees)
                        ))
               {}
               (edit-node-properties etree))))))

(defn can-discard-edit? [x]
  (or (changed? x)
      (is-a? deleted x)
      (is-a? added x)))

(letfn [(vec-remove
          [pos coll]
          (into (subvec coll 0 pos) (subvec coll (inc pos))))]

  (defn discard-edit [node predicate idx]
    (lens/overhaul node
                   edit-node-properties
                   (fn [eprops]
                     (let [metrees (get eprops predicate)
                           metree (nth metrees idx)
                           result-metrees
                           (cond
                             (is-a? maybe-changed metree)
                             (assoc (vec metrees)
                                    idx
                                    (mark-same (maybe-changed-original-value metree)))

                             (is-a? deleted metree)
                             (assoc (vec metrees)
                                    idx
                                    (mark-same (deleted-original-value metree)))

                             (is-a? added metree)
                             (vec-remove idx (vec metrees)))]
                       (if (empty? result-metrees)
                         (dissoc eprops predicate)
                         (assoc eprops predicate result-metrees)))))))

(defn can-refresh? [etree]
  (and (is-a? edit-node etree)
       (empty? (edit-node-properties etree))))

;; re-implementations of wisen.frontend.tree stuff

(def node? (partial is-a? edit-node))

(def node-uri edit-node-uri)

(defn tree-uri
  ([etree]
   (cond
     (is-a? edit-node etree)
     (edit-node-uri etree)

     (ref? etree)
     (ref-uri etree)))

  ([etree uri]
   (cond
     (is-a? edit-node etree)
     (edit-node-uri etree uri)

     (ref? etree)
     (ref-uri etree uri))))

(defn node-type [enode]
  (tree/node-type (edit-tree-result-tree enode)))

(defn graph->edit-tree [graph]
  (make-same-edit-tree (tree/graph->tree graph)))

(defn graph->addit-tree [graph]
  (make-added-edit-tree (tree/graph->tree graph)))

(defn node-object-for-predicate [pred enode]
  (let [metrees (get (edit-node-properties enode) pred)]
    (marked-current (first metrees))))

(defn node-assoc-replace [enode pred etree]
  (-> enode
      (lens/overhaul edit-node-properties
                     (fn [old-props]
                       (reduce (fn [new-props [pred* metrees]]
                                 (if (= pred pred*)
                                   (let [metrees* (mapcat (fn [metree]
                                                            (cond
                                                              (is-a? maybe-changed metree)
                                                              [(mark-deleted metree)]

                                                              (is-a? added metree)
                                                              []

                                                              (is-a? deleted metree)
                                                              [metree]))
                                                          metrees)]
                                     (assoc new-props pred* metrees*))
                                   ;; else
                                   (assoc new-props pred* metrees)))
                               {}
                               old-props)
                       ))
      (lens/overhaul (lens/>> edit-node-properties
                              (lens/member pred))
                     conj
                     (added added-result-value etree))))

(defn make-edit-tree-kind-lens [default-tree-for-kind]
  (fn
    ([etree]
     (cond
       (literal-string? etree)
       tree/literal-string

       (literal-decimal? etree)
       tree/literal-decimal

       (literal-boolean? etree)
       tree/literal-boolean

       (literal-time? etree)
       tree/literal-time

       (literal-date? etree)
       tree/literal-date

       (ref? etree)
       tree/ref

       (edit-node? etree)
       tree/node))
    ([etree kind]
     (if (= kind ((make-edit-tree-kind-lens default-tree-for-kind) etree))
       etree
       (make-added-edit-tree
        (default-tree-for-kind kind))))))

(defn- map-values [f m]
  (reduce (fn [acc [k v]]
            (assoc acc k (f v)))
          {}
          m))

(defn make-edit-node-type-lens [default-node-for-type]
  "TODO: This must be developed closer to a spec."
  (fn
    ([enode]
     (edit-tree-result-tree
      (node-object-for-predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" enode)))
    ([enode type]
     (if (= type ((make-edit-node-type-lens default-node-for-type) enode))
       enode
       (make-added-edit-tree (default-node-for-type type))))))

(defn organization? [node]
  (= "http://schema.org/Organization"
     (tree/type-uri (node-type node))))
(defn- needs-to-be-added? [metrees etree]
  (not (some #(cond
                (added? %)
                (= (added-result-value %)
                   etree)

                (deleted? %)
                false

                (maybe-changed? %)
                (= (maybe-changed-result-value %)
                   etree))
             metrees)))

(defn- smart-conj [metrees etree]
  (if (needs-to-be-added? metrees etree)
    (conj metrees (mark-added etree))
    metrees))

(defn- insert-property [enode prop]
  (lens/overhaul enode (lens/>> edit-node-properties
                                (lens/member (tree/property-predicate prop)))
                 (fn [metrees]
                   (smart-conj metrees
                               (make-added-edit-tree
                                (tree/property-object prop))))))

(defn insert-properties [edit-node tree-properties]
  (reduce insert-property edit-node tree-properties))

(defn- same?* [x]
  (and (is-a? maybe-changed x)
       (let [orig (maybe-changed-original-value x)
             res (maybe-changed-result-value x)]
         (cond
           (and (ref? orig)
                (ref? res))
           (= (ref-uri orig)
              (ref-uri res))

           (and (literal-string? orig)
                (literal-string? res))
           (= (literal-string-value orig)
              (literal-string-value res))

           (and (literal-decimal? orig)
                (literal-decimal? res))
           (= (literal-decimal-value orig)
              (literal-decimal-value res))

           (and (literal-boolean? orig)
                (literal-boolean? res))
           (= (literal-boolean-value orig)
              (literal-boolean-value res))

           (and (literal-time? orig)
                (literal-time? res))
           (= (literal-time-value orig)
              (literal-time-value res))

           (and (literal-date? orig)
                (literal-date? res))
           (= (literal-date-value orig)
              (literal-date-value res))

           (and (many? orig)
                (many? res))
           (every? identity
                   (map same?*
                        (many-edit-trees orig)
                        (many-edit-trees res)))

           (and (exists? orig)
                (exists? res))
           (existential/with-fresh-existential
             (fn [ex]
               (same?* ((exists-k orig) ex)
                       ((exists-k res) ex))))

           (and (is-a? edit-node orig)
                (is-a? edit-node res))
           (= (edit-node-uri orig)
              (edit-node-uri res))

           :else
           false
           ))))

(defn same? [x]
  (existential/with-exgen
    (fn []
      (same?* x))))

(defn changed? [x]
  (and (is-a? maybe-changed x)
       (not (same? x))))

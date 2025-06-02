(ns wisen.frontend.existential
  (:require [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]))

(def existential realm/integer)

(defn existential? [x]
  (realm/contains? existential x))

(defn coerce-existential [uri]
  (if (existential? uri)
    uri
    (hash uri)))

(def existential-generator 0)

(defn next-existential+generator [ex]
  [ex (inc ex)])

(defn split-existential-generator [ex]
  [ex (+ ex 1000)])

;; convenience API

(def ^:dynamic exgen)

(defn with-exgen [f]
  (binding [exgen (atom existential-generator)]
    (f)))

(defn with-fresh-existential [f]
  (assert exgen)
  (let [[ex exgen*] (next-existential+generator @exgen)]
    (reset! exgen exgen*)
    (f ex)))

(defn with-exgen-2 [f]
  (f existential-generator))

(defn with-fresh-existential-2 [exgen f]
  (assert exgen)
  (let [[ex exgen*] (next-existential+generator exgen)]
    (f ex exgen*)))

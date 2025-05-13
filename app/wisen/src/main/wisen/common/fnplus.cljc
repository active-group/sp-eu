(ns wisen.common.fnplus
  (:require [cljs.analyzer :as a]
            [clojure.set :as set]))

#?(:cljs
   (deftype EquivableFn [f form env]
     IFn
     (-invoke [this]
       (f))
     (-invoke [this a]
       (f a))
     (-invoke [this a b]
       (f a b))
     (-invoke [this a b c]
       (f a b c))
     (-invoke [this a b c d]
       (f a b c d))
     (-invoke [this a b c d e]
       (f a b c d e))
     (-invoke [this a b c d e f]
       (f a b c d e f))
     (-invoke [this a b c d e f g]
       (f a b c d e f g))
     (-invoke [this a b c d e f g h]
       (f a b c d e f g h))
     (-invoke [this a b c d e f g h i]
       (f a b c d e f g h i))
     (-invoke [this a b c d e f g h i j]
       (f a b c d e f g h i j))
     (-invoke [this a b c d e f g h i j k]
       (f a b c d e f g h i j k))
     (-invoke [this a b c d e f g h i j k l]
       (f a b c d e f g h i j k l))
     (-invoke [this a b c d e f g h i j k l m]
       (f a b c d e f g h i j k l m))
     (-invoke [this a b c d e f g h i j k l m n]
       (f a b c d e f g h i j k l m n))
     (-invoke [this a b c d e f g h i j k l m n o]
       (f a b c d e f g h i j k l m n o))
     (-invoke [this a b c d e f g h i j k l m n o p]
       (f a b c d e f g h i j k l m n o p))
     (-invoke [this a b c d e f g h i j k l m n o p q]
       (f a b c d e f g h i j k l m n o p q))
     (-invoke [this a b c d e f g h i j k l m n o p q r]
       (f a b c d e f g h i j k l m n o p q r))
     (-invoke [this a b c d e f g h i j k l m n o p q r s]
       (f a b c d e f g h i j k l m n o p q r s))
     (-invoke [this a b c d e f g h i j k l m n o p q r s t]
       (f a b c d e f g h i j k l m n o p q r s t))
     (-invoke [this a b c d e f g h i j k l m n o p q r s t rest]
       (f a b c d e f g h i j k l m n o p q r s t rest))

     IEquiv
     (-equiv [_ other]
       (if (instance? EquivableFn other)
         (and (= form (.-form other))
              (= env (.-env other)))
         false))))

#?(:clj (deftype EquivableFn [f form env]))

(defn- free* [bound ast]
  (let [op (:op ast)]
    (cond
      (= op :local)
      (when (not-any? #{(:name ast)} bound)
        #{(:name ast)})
      
      (= op :let)
      (let [bindings (:bindings ast)]
        (recur (set/union bound
                          (set (map :name bindings)))
               (:body ast)))

      (= op :letfn)
      (let [bindings (:bindings ast)]
        (recur (set/union bound
                          (set (map :name bindings)))
               (:body ast)))

      (= op :loop)
      (let [bindings (:bindings ast)]
        (recur (set/union bound
                          (set (map :name bindings)))
               (:body ast)))

      (= op :fn-method)
      (if-let [body (:body ast)]
        (let [params (:params ast)]
          (recur (set/union bound
                            (set (map :name params)))
                 body))
        #{})

      :else
      (let [ks (:children ast)
            children (map #(get ast %) ks)]
        (mapcat #(cond
                   (map? %) (free* bound %)
                   (coll? %) (mapcat (partial free* bound) %))
                children)))))

(defn free [env form]
  (free* #{} (a/analyze env form)))

(defmacro stabilize [?fn-form]
  (let [fvs (free &env ?fn-form)
        lm (into {}
                 (map (fn [sym]
                        [`(quote ~sym) sym])
                      fvs))]
    `(EquivableFn. ~?fn-form '~?fn-form ~lm)))

(defmacro fn+ [& ?forms]
  (let [fn-form `(fn ~@?forms)]
    `(stabilize ~fn-form)))

(defmacro foo [x y]
  x)

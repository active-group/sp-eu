(ns wisen.frontend.head
  (:require [active.data.realm :as realm]
            [active.data.record
             :refer-macros [def-record]
             :refer [is-a?]]
            [active.clojure.lens :as lens]
            [reacl-c.core :as c :include-macros true]
            [reacl-c-basics.core :as basics]
            [reacl-c-basics.ajax :as ajax]))

(def-record set-head-commit-id-action
  [set-head-commit-id-action-commit-id])

(defn make-set-head-commit-id-action [commit-id]
  (set-head-commit-id-action
   set-head-commit-id-action-commit-id commit-id))

(defn set-head-commit-id-action? [x]
  (is-a? set-head-commit-id-action x))

(defn- load-head-commit-id []
  (c/with-state-as [state last-response :local nil]
    (c/fragment

     (c/handle-state-change

      (c/focus lens/second
               (ajax/fetch (ajax/GET "/api/head")))

      (fn [_ new-state]
        (let [resp (second new-state)]
          (if (and (ajax/response? resp)
                   (ajax/response-ok? resp))
            [(ajax/response-value resp) resp]
            [(first new-state) resp]))))

     (c/focus lens/second
              (c/handle-action
               (basics/interval 15000)
               (fn [_ _]
                 nil))))))

(defn with-head-commit-id [loading k]
  (c/with-state-as [state commit-id :local nil]
    (c/fragment

     (if (some? commit-id)
       (c/handle-action
        (c/focus lens/first
                 (k commit-id))
        (fn [[st comm-id] action]
          (if (set-head-commit-id-action? action)
            (c/return :state [st (set-head-commit-id-action-commit-id action)])
            (c/return :action action))))
       ;; else loading
       loading)

     (c/focus lens/second (load-head-commit-id)))))

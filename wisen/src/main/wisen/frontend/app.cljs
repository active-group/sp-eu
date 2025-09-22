(ns wisen.frontend.app
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [wisen.common.routes :as routes]
            [wisen.frontend.home :as home]
            [wisen.frontend.search :as search]
            [wisen.frontend.create :as create]
            [wisen.frontend.nlp :as nlp]
            [wisen.frontend.edit :as edit]
            [reacl-c-basics.pages.routes :as pages.routes]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.util :as util]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.default :as default]
            [active.data.realm.validation :as validation]
            [wisen.frontend.resource :as resource]
            ["jsonld" :as jsonld]
            [reacl-c-basics.forms.core :as forms]
            [wisen.frontend.translations :as tr]
            [wisen.frontend.context :as context]
            [wisen.frontend.localstorage :as ls]))

(defn menu [ctx]
  (letfn [(new-* [title initial-tree]
            (dom/li
             (modal/modal-button (dom/div
                                  {:style {:display "flex"
                                           :align-items "center"
                                           :gap "0.3em"
                                           :font-weight "normal"}}

                                  (dom/span {:style {:font-size "1.6em"}}
                                            "+ ")

                                  title)
                                 (fn [close-action]
                                   (create/main ctx
                                                initial-tree
                                                (ds/button-secondary
                                                 {:onClick #(c/return :action close-action)}
                                                 (context/text ctx tr/close)))))))

          (new-graph []
            (dom/li
             (modal/modal-button (dom/div
                                  {:style {:display "flex"
                                           :align-items "center"
                                           :gap "0.3em"
                                           :font-weight "normal"}}

                                  (dom/span {:style {:font-size "1.6em"}}
                                            "+ ")

                                  (context/text ctx tr/rdf))
                                 (fn [close-action]
                                   (dom/div
                                    {:style {:min-width "95vw"
                                             :display "flex"
                                             :overflow "auto"}}
                                    (create/from-rdf {:style {:flex 1}}
                                                     ctx
                                                     "{}"
                                                     (ds/button-secondary
                                                      {:onClick #(c/return :action close-action)}
                                                      (context/text ctx tr/close))))))))]

    (ds/padded-2
     {:style {:border-bottom ds/border
              :padding "12px 24px"}}

     (dom/menu {:style {:list-style-type "none"
                        :padding 0
                        :margin 0
                        :display "flex"
                        :align-items "center"
                        :justify-content "space-between"
                        :gap "2em"}}

               (dom/a {:style {:font-weight "bold"}
                       :href (pages.routes/href routes/home)}
                      (context/text ctx tr/search))

               (dom/i {:style {:color "#777"}} (context/commit-id ctx))

               (dom/div
                {:style {:display "flex"
                         :align-items "baseline"
                         :justify-content "flex-end"
                         :gap "2em"}}
                (new-* (context/text ctx tr/organization) default/default-organization)
                (new-* (context/text ctx tr/event) default/default-event)
                (new-* (context/text ctx tr/offer) default/default-offer)
                (new-graph)
                (ds/select
                 (forms/option {:value tr/en} "en")
                 (forms/option {:value tr/de} "de")))))))

(def ^:private lang-local-storage-key
  "language")

(c/defn-item ^:private with-lang [item]
  (c/isolate-state
   nil
   (c/with-state-as lang
     (cond
       (nil? lang)
       ;; try loading intial lang from local state
       (c/handle-action
        (ls/get! lang-local-storage-key)
        (fn [_ s]
          (if (nil? s)
            (c/return :state (tr/initial-language!))
            (c/return :state s))))

       (string? lang)
       (c/fragment
        item
        (c/once
         (fn [s]
           (c/return :action (ls/set! lang-local-storage-key s)))))))))

(defn toplevel []
  (util/with-schemaorg
    (fn [schema]
      (util/with-head-commit-id
        (fn [head-commit-id]
          (dom/div
           {:style {:width "100%"
                    :height "100%"
                    :background "#eee"
                    :display "flex"
                    :flex-direction "column"
                    :overflow "hidden"}}

           (with-lang
             (c/with-state-as lang
               (let [ctx (context/make lang schema head-commit-id)]
                 (c/fragment
                  (menu ctx)
                  (if-let [resource-id (first
                                        (pages.routes/parse routes/resource (.-href (.-location js/window))))]
                    (resource/main ctx resource-id)
                    (search/main ctx))))))))))))

(defn ^:dev/after-load start []
  (println "start")
  (validation/set-checking! true)
  (cmain/run
   (.getElementById js/document "main")
   (toplevel)))

(defn init []
  (println "init")
  (start))

(init)


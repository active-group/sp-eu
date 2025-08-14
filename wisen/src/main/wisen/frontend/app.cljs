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
            [wisen.frontend.context :as context]))

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
                                   (create/main (context/schema ctx)
                                                initial-tree
                                                (ds/button-secondary
                                                 {:onClick #(c/return :action close-action)}
                                                 "Close"))))))

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
                                                     (context/schema ctx)
                                                     ""
                                                     (ds/button-secondary
                                                      {:onClick #(c/return :action close-action)}
                                                      "Close")))))))]

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

(defn toplevel []
  (util/with-schemaorg
    (fn [schema]
      (dom/div
       {:style {:width "100%"
                :height "100%"
                :background "#eee"
                :display "flex"
                :flex-direction "column"
                :overflow "hidden"}}

       (c/isolate-state "de"
                        (c/with-state-as lang
                          (let [ctx (context/make lang schema)]
                            (c/fragment
                             (menu ctx)
                             (if-let [resource-id (first
                                                   (pages.routes/parse routes/resource (.-href (.-location js/window))))]
                               (resource/main (context/schema ctx) resource-id)
                               (search/main (context/schema ctx)))))))))))

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


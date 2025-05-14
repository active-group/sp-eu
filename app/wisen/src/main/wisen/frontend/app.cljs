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
            ["jsonld" :as jsonld]))

(defn menu [schema]
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
                                   (create/main schema
                                                initial-tree
                                                (ds/button-secondary
                                                 {:onClick #(c/return :action close-action)}
                                                 "Close"))))))]

    (ds/padded-2
     {:style {:border-bottom ds/border
              :padding "12px 24px"}}

     (dom/menu {:style {:list-style-type "none"
                        :padding 0
                        :margin 0
                        :display "flex"
                        :align-items "baseline"
                        :justify-content "flex-end"
                        :gap "2em"}}

               (new-* "Organization" default/default-organization)
               (new-* "Event" default/default-event)
               (new-* "Offer" default/default-offer)))))

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

       (menu schema)

       (if-let [resource-id (first
                             (pages.routes/parse routes/resource (.-href (.-location js/window))))]
         (resource/main schema resource-id)
         (search/main schema))))))

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


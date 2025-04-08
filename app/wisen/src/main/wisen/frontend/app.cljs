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
            [reacl-c-basics.pages.core :as routing]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.util :as util]
            ["jsonld" :as jsonld]))

(def search-icon
  (dom/svg
   {:viewBox "0 0 16 16"
    :width "16"
    :height "16"
    :fill "none"
    :xmlns "http://www.w3.org/2000/svg"}
   (dom/path
    {:d "M7.2 12.8a5.6 5.6 0 1 0 0-11.2 5.6 5.6 0 0 0 0 11.2z"
     :stroke "currentColor"
     :stroke-width "1.5"
     :stroke-linecap "round"
     :stroke-linejoin "round"})
   (dom/path
    {:d "m14 14-3-3"
     :stroke "currentColor"
     :stroke-width "1.5"
     :stroke-linecap "round"
     :stroke-linejoin "round"}))
  )

(defn menu []
  (ds/padded-2
   {:style {:border-bottom ds/border
            :padding "12px 24px"}}

   (dom/menu {:style {:list-style-type "none"
                      :padding 0
                      :margin 0
                      :display "flex"
                      :align-items "baseline"
                      :justify-content "space-between"
                      :gap 16}}

             (dom/li
              (dom/a {:href (routes/search)
                      :style {:color "blue"
                              :text-decoration "none"
                              :display "flex"
                              :gap "7px"
                              :align-items "center"}}
                     search-icon
                     (dom/div " Search")))

             (dom/li
              (dom/a {:href (routes/create)
                      :style {:color "blue"
                              :text-decoration "none"}}
                     (dom/span {:style {:font-size "1.4em"}}
                               "+ ")
                     "New resource")))))

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

       (menu)

       (routing/html5-history-router
        {routes/home home/main
         routes/search (partial search/main schema)
         routes/create (partial create/main schema)
         routes/nlp nlp/main
         routes/edit edit/main})))))


(defn ^:dev/after-load start []
  (println "start")
  (cmain/run
   (.getElementById js/document "main")
   (toplevel)))

(defn init []
  (println "init")
  (start))

(init)


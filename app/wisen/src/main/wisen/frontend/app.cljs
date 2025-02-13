(ns wisen.frontend.app
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [reacl-c.main :as cmain]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.display :as display]
            [wisen.frontend.routes :as routes]
            [wisen.frontend.home :as home]
            [wisen.frontend.search :as search]
            [wisen.frontend.create :as create]
            [wisen.frontend.edit :as edit]
            [reacl-c-basics.pages.core :as routing]
            [wisen.frontend.design-system :as ds]
            ["jsonld" :as jsonld]))

(defn init []
  (js/console.log "init"))

(defn menu []
  (ds/padded-2
   {:style {:background "#ddd"
            :border-bottom ds/border}}

   (dom/menu {:style {:list-style-type "none"
                      :padding 0
                      :margin 0
                      :display "flex"
                      :gap 16}}

             (dom/li
              (dom/a {:href (routes/home)}
                     "Wisen Web"))

             (dom/li
              (dom/a {:href (routes/search)}
                     "Search"))

             (dom/li
              (dom/a {:href (routes/create)}
                     "New resource")))))

(c/defn-item toplevel []
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
     routes/search search/main
     routes/create create/main
     routes/edit edit/main})))

(cmain/run
  (.getElementById js/document "main")
  (toplevel))


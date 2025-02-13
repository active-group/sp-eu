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
            ["jsonld" :as jsonld]))

(defn init []
  (js/console.log "init"))

(defn menu []
  (dom/div
   {:style {:padding 24}}

   (dom/div {:style {:display "flex"
                     :gap 8}}

            (dom/a {:href (routes/home)}
                   "Wisen Web")

            (dom/a {:href (routes/search)}
                   "Search")

            (dom/a {:href (routes/create)}
                   "New resource"))))

(c/defn-item toplevel []
  (dom/div
   {:style {:background "#eee"}}

   (menu)

   (routing/html5-history-router
    {routes/home home/main
     routes/search search/main
     routes/create create/main
     routes/edit edit/main})))

(cmain/run
  (.getElementById js/document "main")
  (toplevel))


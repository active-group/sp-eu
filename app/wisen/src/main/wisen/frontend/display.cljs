(ns wisen.frontend.display
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            #_["jsonld" :as jsonld]))

(c/defn-item display-expanded [result-json]
  (dom/div
   "TODO"
   (pr-str result-json)))

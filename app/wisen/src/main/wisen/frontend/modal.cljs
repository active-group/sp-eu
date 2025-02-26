(ns wisen.frontend.modal
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [wisen.frontend.design-system :as ds]
            [active.data.record :as record :refer-macros [def-record]]))

(dom/defn-dom main [attrs & items]
  (apply
   dom/div
   attrs
   items))

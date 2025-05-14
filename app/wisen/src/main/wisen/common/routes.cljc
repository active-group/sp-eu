(ns wisen.common.routes
  (:require #?(:clj [reacl-c-basics.pages.routes :as r])
            #?(:cljs [reacl-c-basics.pages.routes :as r :include-macros true])))

(r/defroutes routes
  (r/defroute home "/")
  (r/defroute resource "/resource/:id/about"))

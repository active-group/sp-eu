(ns wisen.frontend.routes
  (:require #?(:clj [reacl-c-basics.pages.routes :as r])
            #?(:cljs [reacl-c-basics.pages.routes :as r :include-macros true])))

(r/defroutes routes
  (r/defroute home "/")
  (r/defroute search "/search")
  (r/defroute create "/create")
  (r/defroute nlp "/nlp")
  (r/defroute edit "/edit/:id"))

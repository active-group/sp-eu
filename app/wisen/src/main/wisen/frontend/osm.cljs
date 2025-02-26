(ns wisen.frontend.osm
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.design-system :as ds]))

(defn- osm-lookup-request [osm-id]
  (ajax/GET (str "/osm/lookup/" osm-id)
            {:headers {:accept "application/ld+json"}}))

(defn- enter-osm-id []
  (c/with-state-as [osm-id osm-id-local :local ""]
    (dom/div
     (c/focus lens/second (forms/input))
     (dom/button {:onClick (fn [[_ osm-id-local]]
                             [osm-id-local osm-id-local])}
                 "Go"))))

(defn main [& [osm-id]]
  (c/isolate-state
   {:osm-id osm-id
    :response nil
    :trees nil}
   (c/with-state-as state

     (dom/div
      (pr-str state)

      (ds/padded-2
       {:style {:overflow "auto"}}
       (dom/h2 "OSM importer")

       (c/focus :osm-id
                (enter-osm-id))

       (when-let [osm-id (:osm-id state)]
         (c/focus :response
                  (ajax/fetch (osm-lookup-request osm-id))))

       (when-let [response (:response state)]
         (when (and (ajax/response? response)
                    (ajax/response-ok? response))
           (promise/call-with-promise-result
            (rdf/json-ld-string->graph-promise (ajax/response-value response))
            (fn [response-graph]
              (c/once
               (fn [_]
                 (c/return :state {:graph response-graph
                                   :response nil
                                   :osm-id nil})))))))

       (when-let [graph (:graph state)]
         (dom/div
          #_(pr-str graph)
          (editor/readonly graph))
         ))))))

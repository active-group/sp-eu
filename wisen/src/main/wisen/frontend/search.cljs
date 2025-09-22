(ns wisen.frontend.search
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [reacl-c-basics.pages.core :as routing]
            [wisen.frontend.promise :as promise]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.editor :as editor]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [wisen.common.routes :as routes]
            [wisen.frontend.leaflet :as leaflet]
            [wisen.frontend.spinner :as spinner]
            [wisen.frontend.util :as util]
            [wisen.common.or-error :refer [make-success
                                           success?
                                           success-value
                                           make-error]]
            [clojure.string :as string]
            [wisen.frontend.commit :as commit]
            ["jsonld" :as jsonld]
            [wisen.frontend.create :as create]
            [wisen.common.query :as query]
            [wisen.frontend.localstorage :as ls]
            [cljs.reader :as reader]
            [wisen.frontend.search-state :as ss]
            [wisen.frontend.context :as context]
            [wisen.frontend.translations :as tr]))

(defn run-query-result-range [commit-id query result-range]
  (ajax/fetch-once
   (ajax/POST "/api/search"
              {:body (js/JSON.stringify (clj->js {:commit-id commit-id
                                                  :query (query/serialize-query query)
                                                  :range (ss/serialize-result-range result-range)}))
               :headers {:content-type "application/json"}})
   (fn [st new-response]
     (if (ajax/response-ok? new-response)
       (let [body (ajax/response-value new-response)
             m (reader/read-string body)]
         (ss/make-search-response
          (ss/make-graph-as-string (:model m))
          (:relevance m)
          (:total-hits m)))
       ;; else
       (ss/make-error
        (pr-str
         (ajax/response-value new-response)))))))

(c/defn-item pager [ssr]
  (c/with-state-as selected-result-range
    (apply
     dom/div
     {:style {:color "#555"
              :font-weight "normal"
              :margin-bottom "1em"
              :display "flex"
              :gap "2em"
              :flex-wrap "wrap"}}
     (map
      (fn [rr]
        (ds/button-secondary
         {:onClick (fn [_] rr)
          :style {:font-weight (when (= rr selected-result-range)
                                 "bold")}}
         (let [start (ss/result-range-start rr)
               end (ss/result-range-end-inclusive rr)]
           (if (= start end)
             (str (inc start))
             (str
              (inc start)
              " - "
              (inc end))))))
      (ss/search-session-results-pages ssr)))))

(declare hue-for-uri
         tree-geo-positions
         map-label-for-uri)

(defn- hsl [h s l]
  (str "hsl(" h "deg " s "% " l "%)"))

(c/defn-item search-response-graph-component [ctx uri-order]
  (c/with-state-as graph
    (cond
      (ss/graph-as-string? graph)
      (util/json-ld-string->graph
       (ss/graph-as-string-value graph)
       (fn [graph]
         (c/once
          (fn [_]
            (c/return :state (ss/make-graph-as-edit-tree
                              (edit-tree/graph->edit-tree graph)))))))

      (ss/graph-as-edit-tree? graph)
      (c/focus ss/graph-as-edit-tree-value
               (c/with-state-as etree
                 (dom/div
                  {:style {:padding-bottom "72px"}}
                  (editor/edit-tree-component ctx nil true false nil uri-order
                                              (into {}
                                                    (map (fn [[_coords uri]]
                                                           [uri (dom/div
                                                                 {:style {:background-color (hsl (hue-for-uri uri) 100 30)
                                                                          :width "20px"
                                                                          :height "20px"
                                                                          :color "white"
                                                                          :font-weight "bold"
                                                                          :border-radius "100%"
                                                                          :text-align "center"}}
                                                                 ""
                                                                 #_(map-label-for-uri uri)
                                                                 )])
                                                         (ss/graph-geo-positions graph))))
                  (let [show-commit-bar? (not-empty (edit-tree/edit-tree-changeset etree))]
                    (dom/div
                     {:style {:border ds/border
                              :position "absolute"
                              :right "10px"
                              :bottom (if show-commit-bar?
                                        "10px"
                                        "-60px")
                              :transition "bottom 0.3s"
                              :border-radius "4px"
                              :background "#ddd"
                              :z-index "999"}}
                     (commit/main ctx)))))))))

(c/defn-item result-component [ctx query result-range]
  (c/with-state-as result
    (cond
      (ss/error? result)
      (dom/pre (pr-str result))

      (ss/loading? result)
      (c/fragment
       (spinner/main)
       (run-query-result-range (context/commit-id ctx) query result-range))

      (ss/search-response? result)
      (c/focus ss/search-response-graph
               (search-response-graph-component ctx (ss/search-response-uri-order result))))))

;; ---

(def-record focus-query-action
  [focus-query-action-query :- query/query])

(defn make-focus-query-action [q]
  (focus-query-action focus-query-action-query q))

(defn make-filter-component [add-button-label initial-filter item]
  (c/with-state-as state
    (dom/div
     (if-not state
       (ds/button-secondary {:onClick (constantly initial-filter)}
                            add-button-label)
       (dom/div
        {:style {:display "flex"
                 :gap "16px"}}
        (dom/div
         {:style {:background "rgba(255,255,255,0.5)"
                  :border ds/border
                  :border-radius "8px"
                  :padding "8px 12px"
                  :min-width "320px"}}
         item)
        (ds/button-secondary {:onClick (constantly nil)}
                             ds/x-icon))))))

(defn h5 [lbl]
  (dom/h5 {:style {:margin 0
                   :font-size "1em"}}
          lbl))

(c/defn-item filter-thing-type-component []
  (make-filter-component
   "+ Add type filter"
   query/initial-thing-type-filter

   (dom/div
    (h5 "Type is one of ...")
    (dom/p
     (dom/label
      (c/focus (lens/contains query/organization-type)
               (forms/input {:type "checkbox"}))
      "Organization")
     (dom/label
      (c/focus (lens/contains query/event-type)
               (forms/input {:type "checkbox"}))
      "Event")
     (dom/label
      (c/focus (lens/contains query/offer-type)
               (forms/input {:type "checkbox"}))
      "Offer")))))

(c/defn-item filter-target-group-component []
  (make-filter-component
   "+ Add target group filter"
   query/initial-target-group-filter

   (dom/div
    (h5 "Target group is one of ...")
    (dom/p
     (dom/label
      (c/focus (lens/contains query/elderly-target-group)
               (forms/input {:type "checkbox"}))
      "Elderly")
     (dom/label
      (c/focus (lens/contains query/queer-target-group)
               (forms/input {:type "checkbox"}))
      "Queer")
     (dom/label
      (c/focus (lens/contains query/immigrants-target-group)
               (forms/input {:type "checkbox"}))
      "Immigrants")))))

(c/defn-item quick-search [ctx loading?]
  (c/with-state-as [query show-advanced? :local false]
    (let [everything? (query/everything-query? query)]
      (dom/div
       (dom/div
        {:style {:padding "8px"
                 :display "flex"
                 :gap "12px"
                 :justify-content "center"
                 :align-items "baseline"}}
        (forms/form
         {:onSubmit (fn [[query _show-advanced?] event]
                      (.preventDefault event)
                      (c/return :action (make-focus-query-action query)))
          :style {:display "flex"
                  :align-items "baseline"
                  :margin "0"
                  :gap "10px"
                  :border "1px solid rgba(255,255,255,0.8)"
                  :background "rgba(255,255,255,0.5)"
                  :backdrop-filter "blur(20px)"
                  :padding "4px 8px"
                  :border-radius "48px"
                  :box-shadow "0 2px 8px rgba(0,0,0,0.3)"
                  }}

         (c/focus lens/first
                  (c/focus query/query-fuzzy-search-term
                           (ds/input {:size 28
                                      :style {:opacity (when everything? "0.5")
                                              :border-radius "20px"}})))

         (ds/button-primary {:type "submit"
                             :style {:background "#923dd2"
                                     :padding "6px 16px"
                                     :border-radius "20px"
                                     :color "white"}}
                            (if loading?
                              (dom/div
                               {:style {:display "flex"
                                        :align-items "center"
                                        :gap "0.5em"}}
                               (context/text ctx tr/searching)
                               (spinner/main))
                              (if everything?
                                (context/text ctx tr/search-everything)
                                (context/text ctx tr/search)))))

        #_(c/focus lens/second
                 (ds/button-secondary {:onClick not
                                       :style {:background "white"
                                               :padding "6px 12px"
                                               :border-radius "4px"}}
                                      (c/with-state-as show?
                                        (if show?
                                          "Hide filters"
                                          "Show filters")))))

       (when show-advanced?
         (c/focus lens/first
                  (dom/div
                   {:style {:background "rgba(255,255,255,0.5)"
                            :backdrop-filter "blur(20px)"
                            :padding "6px 16px"
                            :border-top "1px solid #bbb"
                            }}
                   (dom/h4 "Additional search filters")

                   (dom/div
                    {:style {:display "flex"
                             :flex-direction "column"
                             :gap "8px"}}
                    (c/focus query/query-filter-thing-type
                             (filter-thing-type-component))

                    (c/focus query/query-filter-target-group
                             (filter-target-group-component))))))))))

(defn- hue-for-uri [uri]
  (mod (hash uri) 360))

(defn- map-label-for-uri [uri]
  (let [ascii-int (+ (.charCodeAt \A 0)
                     (mod (hash uri) 26))]
    (char ascii-int)))

(def ^:private startup-query-local-storage-key
  "startup-query")

(defn- query->string [q]
  (pr-str (query/serialize-query q)))

(defn- string->query [s]
  (query/deserialize-query
   (reader/read-string s)))

(c/defn-item ^:private with-startup-query [item]
  (c/isolate-state
   nil
   (c/with-state-as query
     (cond
       (nil? query)
       ;; try loading intial query from local state
       (c/handle-action
        (ls/get! startup-query-local-storage-key)
        (fn [_ s]
          (try
            (string->query s)
            (catch js/Object e
              query/initial-query))))

       (query/query? query)
       (c/fragment
        item
        (c/once
         (fn [q]
           (c/return :action (ls/set! startup-query-local-storage-key
                                      (query->string q))))))))))

(c/defn-item map-search [ctx loading? pins]
  (with-startup-query
    (c/fragment

     (c/focus (lens/>> query/query-geo-bounding-box
                       query/geo-bounding-box<->vectors)
              (leaflet/main {:style {:height 560}} pins))

     (dom/div
      {:style {:position "sticky"
               :top 0
               :z-index 999
               :background "#eee"
               :border-bottom ds/border}}

      (quick-search ctx loading?)))))

(c/defn-item search-session-component [ctx]
  (c/with-state-as search-session
    (let [query (ss/search-session-query search-session)
          ssr (ss/search-session-results search-session)
          selected-result-range (ss/search-session-selected-result-range search-session)]
      (c/fragment

       (ds/padded-2
        (if (query/everything-query? query)
          (dom/h2 (context/text ctx tr/results))
          (dom/h2
           (context/text-fn ctx tr/results-for (query/query-fuzzy-search-term query)))))

       (c/focus ss/search-session-selected-result-range
                (ds/padded-2
                 (pager ssr)))

       (ds/padded-2
        (c/focus ss/search-session-selected-result
                 (result-component ctx query selected-result-range)))))))

(c/defn-item main* [ctx]
  (c/with-state-as search-state
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :overflow "auto"}}

     (dom/div
      {:class "map-and-search-results"
       :style {:overflow "auto"
               :flex 1
               :scroll-behavior "smooth"}}

      (c/handle-action
       (map-search ctx
                   (ss/search-state-some-loading? search-state)
                   (map (fn [[coords uri]]
                          (leaflet/make-pin
                           (map-label-for-uri uri)
                           (hue-for-uri uri)
                           coords
                           (str "#" uri)))
                        (ss/search-state-geo-positions search-state)))
       (fn [search-state action]
         (if (is-a? focus-query-action action)
           (c/return :state
                     (ss/create-search-session
                      (focus-query-action-query action)))
           (c/return))))

      ;; display when we have a graph
      (if (ss/search-session? search-state)
        (search-session-component ctx)
        (ds/padded-2 (context/text ctx tr/no-results-yet)))))))

(c/defn-item main [ctx]
  (c/isolate-state
   ss/initial-search-state
   (c/with-state-as state
     (main* ctx))))

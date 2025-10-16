(ns wisen.frontend.editor
  (:require [reacl-c.core :as c :include-macros true]
            [reacl-c.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]
            [reacl-c-basics.forms.core :as forms]
            [reacl-c-basics.ajax :as ajax]
            [wisen.frontend.design-system :as ds]
            [wisen.frontend.rdf :as rdf]
            [wisen.frontend.tree :as tree]
            [wisen.frontend.edit-tree :as edit-tree]
            [wisen.frontend.change :as change]
            [wisen.common.change-api :as change-api]
            [wisen.frontend.default :as default]
            [wisen.frontend.existential :as existential]
            [wisen.frontend.osm :as osm]
            [active.data.record :as record :refer [is-a?] :refer-macros [def-record]]
            [active.data.realm :as realm]
            [wisen.frontend.modal :as modal]
            [wisen.frontend.details :as details]
            [wisen.frontend.schemaorg :as schemaorg]
            [wisen.frontend.util :as util]
            [wisen.common.or-error :refer [success? success-value error? error-value]]
            [wisen.frontend.leaflet :as leaflet]
            [wisen.frontend.schema :as schema]
            [wisen.frontend.spinner :as spinner]
            [wisen.common.prefix :as prefix]
            [wisen.frontend.value-node :as value-node]
            [wisen.common.wisen-uri :as wisen-uri]
            [wisen.frontend.ask-ai :as ask-ai]
            [clojure.string :as string]
            [wisen.frontend.context :as context]
            [wisen.frontend.translations :as tr]
            [wisen.common.urn :as urn]))

(def-record discard-edit-action
  [discard-edit-action-predicate
   discard-edit-action-index])

(def-record set-reference-action
  [set-reference-action-old-uri :- tree/URI
   set-reference-action-new-uri :- tree/URI
   set-reference-action-predicate :- (realm/optional realm/string)
   set-reference-action-subject-uri :- (realm/optional tree/URI)
   ])

(defn- style-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    {:border "1px solid red"
     :color "red"}

    (edit-tree/added? marked)
    {:border "1px solid green"
     :color "green"}

    (edit-tree/same? marked)
    {:border "1px solid gray"}

    (edit-tree/changed? marked)
    {:border "1px solid orange"
     :color "orange"}))

(defn- icon-for-marked [marked]
  (cond
    (edit-tree/deleted? marked)
    ds/minus-icon

    (edit-tree/added? marked)
    ds/plus-icon

    (edit-tree/same? marked)
    nil

    (edit-tree/changed? marked)
    ds/dot-icon))

(defn- pr-uri [uri]
  (if (existential/existential? uri)
    (dom/span {:style {:font-style "italic"}}
              (tree/uri-string uri))
    (tree/uri-string uri)))

(defn- pr-boolean [ctx on?]
  (if on?
    (context/text ctx tr/is-true)
    (context/text ctx tr/is-false)))

(c/defn-item ^:private before-after [ctx before-item after-item background-color]
  (c/with-state-as [state before-or-after :local ::after]
    (dom/div
     {:style {:display "flex"
              :flex-direction "column"}}

     (c/focus lens/second
              (dom/div
               {:style {:display "flex"}}
               (dom/div {:style {:border-top "1px solid gray"
                                 :border-left "1px solid gray"
                                 :border-right "1px solid gray"
                                 :border-bottom (if (= before-or-after ::before)
                                                  (str "1px solid " background-color)
                                                  "1px solid gray")
                                 :border-top-left-radius "4px"
                                 :padding "0.5ex 0.5em"
                                 :cursor "pointer"
                                 :color "red"
                                 :position "relative"
                                 :top "1px"
                                 :font-weight (if (= before-or-after ::before)
                                                "bold"
                                                "normal")}
                         :onClick (constantly ::before)}
                        (context/text ctx tr/before))
               (dom/div {:style {:border-top "1px solid gray"
                                 :border-right "1px solid gray"
                                 :border-bottom (if (= before-or-after ::after)
                                                  (str "1px solid " background-color)
                                                  "1px solid gray")
                                 :border-top-right-radius "4px"
                                 :padding "0.5ex 0.5em"
                                 :cursor "pointer"
                                 :color "green"
                                 :position "relative"
                                 :top "1px"
                                 :font-weight (if (= before-or-after ::after)
                                                "bold"
                                                "normal")}
                         :onClick (constantly ::after)}
                        (context/text ctx tr/after))))

     (dom/div
      {:style {:border "1px solid gray"
               :padding "1ex 1em"}}
      (c/focus lens/first
               (case before-or-after
                 ::before
                 before-item

                 ::after
                 after-item))))))

(defn- label-for-kind [ctx kind]
  (cond
    (= kind tree/literal-string)
    (context/text ctx tr/string)

    (= kind tree/literal-decimal)
    (context/text ctx tr/decimal)

    (= kind tree/literal-boolean)
    (context/text ctx tr/bool)

    (= kind tree/literal-time)
    (context/text ctx tr/time)

    (= kind tree/literal-date)
    (context/text ctx tr/date)

    (= kind tree/ref)
    (context/text ctx tr/node)

    (= kind tree/node)
    (context/text ctx tr/node)))

(declare edit-tree-component*)

(let [d 0.001]
  (defn- view-box-around [[lat long]]
    [[(- lat d) (+ lat d)]
     [(- long d) (+ long d)]]))

(declare day-of-week-component
         opening-hours-specification-component
         postal-address-component
         edit-node-is-postal-address-value?
         edit-node-is-opening-hours-specification-value?)

(c/defn-item ^:private component-for-predicate [predicate ctx types editable? editing? background-color compare-edit-tree adornment]
  (c/with-state-as etree
    (cond
      (and (= predicate "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
           (or (edit-tree/edit-node? etree)
               (edit-tree/ref? etree)))
      (c/focus edit-tree/tree-uri
               (apply
                ds/select
                {:value (edit-tree/tree-uri etree)
                 :onChange (fn [old-uri e]
                             (let [new-uri (.-value (.-target e))]
                               (c/return :action (set-reference-action
                                                  set-reference-action-old-uri old-uri
                                                  set-reference-action-new-uri new-uri
                                                  set-reference-action-predicate predicate))))
                 :disabled (when-not editing? "disabled")}
                (map (fn [type-uri]
                       (dom/option {:value type-uri}
                                   (schema/label-for-type (context/schema ctx) type-uri)))
                     (sort
                      (distinct
                       (conj types
                             (edit-tree/tree-uri etree)))))))

      (and (= predicate "http://schema.org/logo")
           (or (edit-tree/edit-node? etree)
               (edit-tree/ref? etree)))
      (let [url (edit-tree/tree-uri etree)]
        (dom/div
         {:style {:height "100px"
                  :border ds/border
                  :border-radius "12px"
                  :overflow "hidden"}}
         (dom/img {:src url
                   :height 100})))

      (and (= predicate "http://schema.org/dayOfWeek")
           (or (edit-tree/edit-node? etree)
               (edit-tree/ref? etree)))
      (day-of-week-component ctx editable? editing?)

      (= predicate "http://schema.org/name")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/input+focus {:disabled (when-not editing? "disabled")
                                :style {:font-size "2em"}}))

      (= predicate "http://schema.org/description")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/textarea+focus {:style {:min-width "50em"
                                           :min-height "6em"}
                                   :disabled (when-not editing?
                                               "disabled")}))

      (= predicate "http://schema.org/keywords")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/input+focus {:disabled (when-not editing? "disabled")}))

      (and (= predicate "http://schema.org/byDay")
           (or (edit-tree/edit-node? etree)
               (edit-tree/ref? etree)))
      (day-of-week-component ctx editable? editing?)

      (and (= predicate "http://schema.org/email")
           (edit-tree/literal-string? etree)
           (not editing?))
      (let [s (edit-tree/literal-string-value etree)
            strip-mailto-prefix (fn [s]
                                  (if (re-find #"^mailto:" s)
                                    (subs s
                                          (count "mailto:"))
                                    s))]
        (dom/a {:href s}
               (strip-mailto-prefix s)))

      (and (= predicate "http://schema.org/url")
           (not editing?)
           (or (edit-tree/literal-string? etree)
               (and (edit-tree/edit-node? etree)
                    (tree/uri? (edit-tree/edit-node-uri etree)))))
      (let [uri (cond
                  (edit-tree/literal-string? etree) (edit-tree/literal-string-value etree)
                  (edit-tree/edit-node? etree) (edit-tree/edit-node-uri etree))]
        (dom/a {:href uri} uri))

      #_#_(and
           (= predicate "http://schema.org/openingHoursSpecification")
           (edit-tree/edit-node? etree)
           (= (tree/type-uri (edit-node-type etree)) "http://schema.org/OpeningHoursSpecification")
           (edit-node-is-opening-hours-specification-value? etree))
      (value-node/as-value-node
       (opening-hours-specification-component schema editable? editing?))

      (= predicate "http://schema.org/eventAttendanceMode")
      (c/focus edit-tree/tree-uri
               (ds/select
                {:disabled (when-not editing? "disabled")
                 :style {:padding "7px 8px"}}
                (forms/option {:value "http://schema.org/OfflineEventAttendanceMode"} "Offline")
                (forms/option {:value "http://schema.org/OnlineEventAttendanceMode"} "Online")
                (forms/option {:value "http://schema.org/MixedEventAttendanceMode"} "Mixed")))

      (= predicate "http://schema.org/url")
      (cond
        (edit-tree/literal-string? etree)
        (c/focus (lens/pattern [edit-tree/literal-string-value
                                edit-tree/literal-string-focused?])
                 (ds/input+focus {:type "url"
                                  :placeholder "https://example.com"
                                  :disabled (when-not editing? "disabled")}))

        (edit-tree/edit-node? etree)
        (c/focus (lens/pattern [edit-tree/edit-node-uri
                                edit-tree/edit-node-focused?])
                 (ds/input+focus {:type "url"
                                  :placeholder "https://example.com"
                                  :disabled (when-not editing? "disabled")})))

      #_#_(and
       (= predicate "http://schema.org/address")
       (edit-tree/edit-node? etree)
       (= (tree/type-uri (edit-node-type etree)) "http://schema.org/PostalAddress")
       (edit-node-is-postal-address-value? etree))
      (value-node/as-value-node
       (postal-address-component schema editable? editing?))

      (= predicate "https://wisen.active-group.de/target-group")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              (lens/>> edit-tree/literal-string-focused?
                                       wisen.frontend.forms/selection-simplify)])
               (ds/select+focus
                {:disabled (when-not editing? "disabled")}
                (forms/option {:value "elderly"} (context/text ctx tr/elderly))
                (forms/option {:value "queer"} (context/text ctx tr/queer))
                (forms/option {:value "immigrants"} (context/text ctx tr/immigrants))))

      :else
      (dom/div
       {:style {:display "flex"
                :flex-wrap "wrap"
                :align-items "flex-start"
                :gap "0.5em"}}
       (when editing?
         (c/focus (edit-tree/make-edit-tree-kind-lens
                   (partial default/default-tree-for-predicate-and-kind predicate))
                  (c/with-state-as kind
                    (apply
                     ds/select
                     {:disabled (when-not editing? "disabled")}
                     (map (fn [kind]
                            (forms/option {:value kind} (label-for-kind ctx kind)))
                          (distinct
                           (conj (schema/kinds-for-predicate (context/schema ctx) predicate)
                                 kind)))))))
       (edit-tree-component*
        ctx
        (schema/types-for-predicate (context/schema ctx) predicate)
        editable?
        editing?
        background-color
        compare-edit-tree
        adornment)))))

(c/defn-item add-property-button [ctx predicates]
  (c/with-state-as [node predicate :local schemaorg/default-predicate]
    (dom/div
     {:style {:display "flex"
              :gap "1em"}}
     (dom/div
      {:style {:display "flex"
               :border "1px solid #888"
               :border-radius "3px"}}
      (c/focus lens/second
               (apply
                ds/select
                {:style {:border 0
                         :border-right "1px solid #888"
                         :border-top-right-radius "0px"
                         :border-bottom-right-radius "0px"}}
                (map (fn [pred]
                       (forms/option {:value pred} (schema/label-for-predicate ctx pred)))
                     predicates)))

      (ds/button-primary
       {:style {:padding "2px 12px"
                :color "#444"
                :background "#ddd"
                :font-weight "normal"}
        :onClick
        (fn [[node predicate] _]
          [(edit-tree/edit-node-add-property node predicate (default/default-tree-for-predicate (context/schema ctx) predicate))
           predicate])}
       (context/text ctx tr/add-property))))))

;; OSM

(defn- pr-osm-uri [ctx uri]
  (dom/a {:href uri}
         (context/text ctx tr/view-on-open-street-map)))

(declare readonly-graph)

(c/defn-item osm-importer [ctx osm-uri]
  (c/with-state-as graph
    (c/fragment

     (when (some? osm-uri)
       (util/load-json-ld-state
        (osm/osm-lookup-request osm-uri)))

     (when graph (readonly-graph ctx graph)))))

(c/defn-item link-organization-with-osm-button [ctx osm-uri close-action]
  (c/with-state-as [node local-state :local {:graph nil
                                             :osm-uri osm-uri
                                             :focus? (wisen.frontend.forms/make-selected 0 0)
                                             :commit-osm-uri nil}]
    (dom/div
     (modal/padded
      (dom/h3 (if osm-uri "Update " "Link with ") "OpenStreetMap")

      (c/focus (lens/pattern [(lens/>> lens/second :osm-uri)
                              (lens/>> lens/second :focus?)])
               (ds/input+focus
                {:type "url"
                 :placeholder "https://www.openstreetmap.org/..."}))

      (c/focus (lens/>> lens/second :graph)
               (osm-importer ctx (:commit-osm-uri local-state))))

     (modal/toolbar
      (ds/button-secondary {:onClick #(c/return :action close-action)}
                           "Cancel")

      (c/focus lens/second
               (ds/button-primary {:onClick (fn [ls]
                                              (assoc ls :commit-osm-uri (:osm-uri ls)))}
                                  "Ask OSM!"))

      (when (:graph local-state)
        (ds/button-primary
         {:onClick (fn [[node local-state]]
                     (let [place-node (edit-tree/graph->edit-tree (:graph local-state))]
                       (assert (edit-tree/edit-node? place-node))
                       (c/return :state [(osm/organization-do-link-osm
                                          node
                                          (:osm-uri local-state)
                                          place-node)
                                         (-> local-state
                                             (dissoc :graph)
                                             (dissoc :commit-osm-uri))]
                                 :action close-action)))}
         "Add properties as 'location'"))))))

;; ---

(declare the-circle)

(defn- ask-ai [ctx close-action]
  (dom/div
   {:style {:display "flex"
            :flex-direction "column"
            :overflow "auto"}}

   (modal/padded
    {:style {:overflow "auto"}}
    (ask-ai/main
     (partial readonly-graph ctx)
     close-action))

   (modal/toolbar
    (ds/button-secondary {:onClick #(c/return :action close-action)}
                         "Cancel"))))

(defn- set-properties [ctx]
  (c/with-state-as node
    (dom/div {:style {:display "flex"
                      :gap "2em"
                      :align-items "center"}}

             (dom/div
              {:style {:display "flex"}}
              (add-property-button ctx (schema/predicates-for-type (context/schema ctx) (edit-tree/node-type node))))

             (dom/div
              {:style {:display "flex"
                       :gap "1em"}}

              #_(when (edit-tree/organization? node)
                (if-let [osm-uri (osm/node-osm-uri node)]

                  (dom/div
                   {:style {:display "flex"
                            :gap "1em"}}
                   (pr-osm-uri ctx osm-uri)
                   (modal/modal-button (context/text ctx tr/update) #(link-organization-with-osm-button ctx osm-uri %)))
                  (modal/modal-button (context/text ctx tr/link-with-open-street-map) #(link-organization-with-osm-button ctx nil %))))

              (modal/modal-button (ds/lightbulb-icon "21") (partial ask-ai ctx))))))

(defn- valid-uri? [x]
  (and (string? x)
       (re-find #"https?://[a-zA-z0-9]+" x)))

(defn- set-reference [ctx close-action]
  (c/with-state-as node
    (c/local-state
     (let [uri (edit-tree/tree-uri node)]
       [uri (wisen.frontend.forms/make-selected 0 (count (str uri)))])
     (forms/form
      {:onSubmit
       (fn [[node [new-uri _]] e]
         (.preventDefault e)
         (let [old-uri (edit-tree/tree-uri node)]
           (c/return :action (set-reference-action
                              set-reference-action-old-uri old-uri
                              set-reference-action-new-uri (or
                                                            (existential/string->existential new-uri)

                                                            new-uri))
                     :action close-action)))}
      (modal/padded
       (dom/h3
        (context/text ctx tr/set-as-reference-to-another-node))

       (c/focus lens/second (ds/input+focus {:size 80})))

      (modal/toolbar
       (ds/button-secondary {:type "button"
                             :onClick #(c/return :action close-action)}
                            (context/text ctx tr/cancel))
       (ds/button-primary {:type "submit"}
                          (context/text ctx tr/set-reference)))))))

(defn- refresh-node-request [uri]
  (ajax/GET (if (urn/urn? uri)
              (prefix/resource-description uri)
              uri)
            {:headers {"accept" "application/ld+json"}}))

(c/defn-item ^:private refresh-node [uri]
  (util/load-json-ld
   (refresh-node-request uri)))

(defn- refresh-button [ctx]
  (c/with-state-as [node refresh-state :local ::idle]
    (case refresh-state
      ::idle
      (c/focus lens/second
               (ds/button-primary {:onClick #(c/return :state ::run)}
                                  (context/text ctx tr/refresh)))

      ::run
      (-> (refresh-node (edit-tree/tree-uri node))
          (c/handle-action (fn [[enode _] ac]
                             (cond
                               (success? ac)
                               (let [new-graph (success-value ac)
                                     new-node (tree/graph->tree new-graph)]
                                 [(edit-tree/set-edit-node-original enode new-node) ::idle])

                               (error? ac)
                               [enode (error-value ac)]))))

      ;; failure
      (dom/span {:style {:color "red"}
                 :title (pr-str refresh-state)}
                (context/text ctx tr/refresh-failed)
                " "
                (c/focus lens/second
                         (ds/button-primary {:onClick #(c/return :state ::run)}
                                            (context/text ctx tr/retry-refresh)))))))

(defn- the-circle [& children]
  (apply dom/div
         {:style {:border "1px solid #777"
                    :background "white"
                    :border-radius "100%"
                    :width "27px"
                  :height "27px"}}
         children))

(c/defn-item property-object-component [ctx types predicate editable? force-editing? last-property? background-color compare-edit-tree adornment]
  (c/with-state-as marked-edit-trees
    (apply dom/div
           {:style {:display "flex"
                    :flex-direction "column"
                    :gap "2ex"}}
           (map-indexed (fn [idx _]
                          (c/focus (lens/at-index idx)
                                   (c/with-state-as marked-edit-tree
                                     (dom/div
                                      {:style {:flex 1
                                               :display "flex"
                                               :position "relative"}}

                                      (let [last-marked-edit-tree? (= idx (dec (count marked-edit-trees)))]
                                        (when (and last-property?
                                                   last-marked-edit-tree?)
                                          (dom/div {:style {:width "1px"
                                                            :border-left (if force-editing?
                                                                           "1px dashed gray"
                                                                           (str "1px solid " background-color))
                                                            :height "100%"
                                                            :position "absolute"
                                                            :top "15px"
                                                            :z-index "5"
                                                            :left "-1px"
                                                            :background background-color}})))

                                      (dom/div
                                       {:style {:min-width "15em"
                                                :position "relative"
                                                :display "flex"
                                                :gap "0.5em"
                                                :justify-content "space-between"
                                                :padding-right "1em"
                                                :align-items "flex-start"}}

                                       (dom/div
                                        {:style {:display "flex"
                                                 :margin "0 24px"}}
                                        (dom/div
                                         {:style (merge {:background "white"
                                                         :display "inline-flex"
                                                         :gap "0.3em"
                                                         :align-items "center"
                                                         :border (str "1px solid gray")
                                                         :padding "4px 12px"
                                                         :position "relative"
                                                         :z-index "5"}
                                                        (style-for-marked marked-edit-tree))}
                                         (icon-for-marked marked-edit-tree)
                                         (dom/a
                                          {:href predicate
                                           :target "_blank"
                                           :style {:color "inherit"
                                                   :text-decoration "none"}}
                                          (schema/label-for-predicate ctx predicate)))

                                        (when (and (edit-tree/can-discard-edit? marked-edit-tree)
                                                   force-editing?)
                                          (ds/button-secondary
                                           {:style
                                            {:border-color "gray"
                                             :border-style "solid"
                                             :border-width "1px 1px 1px 0"
                                             :background "#ddd"
                                             :padding "4px 12px"
                                             :position "relative"
                                             :z-index "5"}
                                            :onClick
                                            #(c/return :action
                                                       (discard-edit-action
                                                        discard-edit-action-predicate
                                                        predicate
                                                        discard-edit-action-index
                                                        idx))}
                                           ds/x-icon)))

                                       (when (and force-editing?
                                                  (edit-tree/can-delete? marked-edit-tree))
                                         (ds/button-secondary {:onClick edit-tree/mark-deleted
                                                               :style {:font-size "25px"
                                                                       :font-weight "normal"
                                                                       :cursor "pointer"
                                                                       :z-index 5}}
                                                              "×"))


                                       (dom/div {:style {:width "100%"
                                                         :border-bottom "1px solid gray"
                                                         :position "absolute"
                                                         :top "14px"
                                                         :z-index "4"}}))

                                      (cond
                                        (edit-tree/deleted? marked-edit-tree)
                                        (c/focus edit-tree/deleted-original-value
                                                 (component-for-predicate predicate ctx types false false background-color compare-edit-tree adornment))

                                        (edit-tree/added? marked-edit-tree)
                                        (c/focus edit-tree/added-result-value
                                                 (component-for-predicate predicate ctx types editable? force-editing? background-color compare-edit-tree adornment))

                                        (edit-tree/same? marked-edit-tree)
                                        (c/focus edit-tree/maybe-changed-result-value
                                                 (component-for-predicate predicate ctx types editable? force-editing? background-color compare-edit-tree adornment))

                                        (edit-tree/changed? marked-edit-tree)
                                        (before-after
                                         ctx
                                         ;; before
                                         (c/focus edit-tree/maybe-changed-original-value
                                                  (component-for-predicate predicate ctx types false false background-color compare-edit-tree adornment))
                                         ;; after
                                         (c/focus edit-tree/maybe-changed-result-value
                                                  (component-for-predicate predicate ctx types editable? force-editing? background-color compare-edit-tree adornment))

                                         background-color))))))

                        marked-edit-trees))))

(c/defn-item properties-component [ctx types editable? force-editing? background-color compare-edit-tree adornment]
  (c/with-state-as properties

    (apply
     dom/div
     {:style {:display "flex"
              :flex-direction "column"
              :gap "2ex"}}

     (let [ks (sort schemaorg/compare-predicate (keys properties))]
       (map-indexed (fn [idx predicate]
                      (-> (c/focus (lens/member predicate)
                                   (let [last? (= idx (dec (count ks)))]
                                     (property-object-component ctx types predicate editable? force-editing? last? background-color compare-edit-tree adornment)))
                          (c/handle-action
                           (fn [_ action]
                             (if (and (is-a? set-reference-action action)
                                      (nil? (set-reference-action-predicate action)))
                               (c/return :action (set-reference-action-predicate action predicate))
                               (c/return :action action))))))

                    ks)))))

(c/defn-item ^:private references-indicator [ctx id editing?]
  (c/with-state-as [enode result :local nil]
    (cond
      (nil? result)
      (c/fragment
       (spinner/main
        (context/text ctx tr/determining-references))
       (c/focus lens/second
                (ajax/fetch (ajax/GET (str "/api/references/" id)
                                      {:params {"base-commit-id" (context/commit-id ctx)}}))))

      (and (ajax/response? result)
           (ajax/response-ok? result))
      (let [references (ajax/response-value result)
            n (count references)]
        (if (> n 0)
          (ds/popover-button
           (context/text-fn ctx tr/n-references-in-total n)
           (apply dom/ul
                  (map (fn [reference]
                         (let [uri (:uri reference)]
                           (dom/li
                            (when-let [name (:name reference)]
                              (str name " – "))
                            (dom/a {:href
                                    (if (urn/urn? uri)
                                      (prefix/resource-description uri)
                                      uri)}
                                   uri))))
                       references)))
          (c/fragment
           (context/text ctx tr/no-references)
           (when editing?
             (c/focus lens/first
                      (ds/button-primary {:onClick edit-tree/edit-node-mark-all-properties-deleted}
                                         "Delete resource"))
             )))))))

(c/defn-effect copy-to-clipboard! [s]
  (.writeText (.-clipboard js/navigator) s))

(defn- node-component [ctx types editable? force-editing? background-color compare-edit-tree adornment]
  (c/with-state-as [node editing? :local force-editing?]

    (let [uri (edit-tree/edit-node-uri node)
          ado (when adornment
                (adornment uri))]

      (dom/div

       (dom/div
        {:style {:color "#555"
                 :display "flex"
                 :align-items "center"
                 :gap "1em"}}

        (dom/div {:style {:border "1px solid #777"
                          :padding (if ado
                                     "4px 20px 4px 4px"
                                     "4px 20px")
                          :font-size "13px"
                          :background "white"
                          :border-radius "25px"
                          :display "flex"
                          :gap "0.8em"
                          :align-items "center"}}

                 ado

                 (if (existential/existential? uri)
                   (dom/div
                    {:style {:display "flex" :gap "0.5em" :align-items "baseline"}}
                    (dom/div {:style {:color "green"
                                      :position "relative"
                                      :top "3px"}}
                             ds/plus-icon)
                    (pr-uri uri))
                   (dom/a
                    (let [uri-string (tree/uri-string uri)]
                      {:id uri-string
                       :href (if (urn/urn? uri-string)
                               (prefix/resource-description uri-string)
                               uri-string)})
                    (pr-uri uri)))

                 (when (or (not (existential/existential? uri))
                           force-editing?)
                   (ds/button-primary
                    {:onClick #(c/return :action (copy-to-clipboard! uri))}
                    (context/text ctx tr/copy)))

                 (when editing?
                   (c/fragment
                    (c/focus lens/first
                             (modal/modal-button (context/text ctx tr/set-reference)
                                                 (partial set-reference ctx))))))

        (when editable?
          (c/focus lens/second
                   (ds/button-primary {:onClick not}
                                      (if editing?
                                        (context/text ctx tr/done)
                                        (context/text ctx tr/edit)))))

        (when (wisen-uri/is-wisen-uri? uri)
          (c/focus lens/first
                   (references-indicator ctx (wisen-uri/wisen-uri-id uri) editing?))))

       (c/focus
        lens/first
        (dom/div

         (-> (let [eprops (edit-tree/edit-node-properties node)
                   margin-left "14px"]
               (if-not (empty? eprops)
                 ;; show properties
                 (dom/div
                  {:style {:display "flex"
                           :flex-direction "column"
                           :gap "2ex"
                           :margin-left margin-left
                           :padding-top "12px"
                           :border-left "1px solid gray"
                           :padding-bottom "2ex"}}

                  (c/focus edit-tree/edit-node-properties
                           (properties-component ctx types editable? editing? background-color compare-edit-tree adornment)))
                 ;; show small dashed spacer
                 (dom/div {:style {:margin-left margin-left
                                   :border-left "1px dashed gray"
                                   :padding "1ex 2em"
                                   :font-style "italic"
                                   :display "flex"
                                   :gap "1em"}}
                          (dom/div (context/text ctx tr/no-properties))
                          (refresh-button ctx))))
             (c/handle-action
              (fn [node action]
                (cond
                  (is-a? discard-edit-action action)
                  (edit-tree/discard-edit node
                                          (discard-edit-action-predicate action)
                                          (discard-edit-action-index action))

                  (and (is-a? set-reference-action action)
                       (nil? (set-reference-action-subject-uri action)))
                  (c/return :action (set-reference-action-subject-uri action (edit-tree/edit-node-uri node)))

                  :else
                  (c/return :action action)))))

         (when editing?
           (set-properties ctx))))))))

(letfn [(check-prop [pred matches? etree]
          (let [eprops (edit-tree/edit-node-properties etree)]
            (let [marked (lens/yank eprops (lens/>> (lens/member pred) lens/first))]
              (and (or (edit-tree/added? marked)
                       (edit-tree/maybe-changed? marked))
                   (matches? (edit-tree/marked-result-value marked))))))]

  (defn- edit-node-is-geo-coordinates-value? [etree]
    (and
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (or (edit-tree/edit-node? x)
                            (edit-tree/ref? x))
                        (= "http://schema.org/GeoCoordinates"
                           (edit-tree/tree-uri x))))
                 etree)
     (check-prop "http://schema.org/latitude" edit-tree/literal-decimal? etree)
     (check-prop "http://schema.org/longitude" edit-tree/literal-decimal? etree)))

  (defn- edit-node-is-postal-address-value? [etree]
    (and
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (edit-tree/edit-node? x)
                        (= "http://schema.org/PostalAddress"
                           (edit-tree/edit-node-uri x))))
                 etree)
     (check-prop "http://schema.org/streetAddress" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/postalCode" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/addressLocality" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/addressCountry" edit-tree/literal-string? etree)))

  (defn- edit-node-is-opening-hours-specification-value? [etree]
    (and
     (check-prop "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                 (fn [x]
                   (and (edit-tree/edit-node? x)
                        (= "http://schema.org/OpeningHoursSpecification"
                           (edit-tree/edit-node-uri x))))
                 etree)
     (check-prop "http://schema.org/dayOfWeek" edit-tree/edit-node? etree)
     (check-prop "http://schema.org/opens" edit-tree/literal-string? etree)
     (check-prop "http://schema.org/closes" edit-tree/literal-string? etree))))

(c/defn-item ^:private geo-coordinates-component [ctx editable? force-editing?]
  (c/with-state-as eprops
    (let [latitude-lens (lens/>>
                         (lens/>>
                          (lens/member "http://schema.org/latitude")
                          lens/first
                          edit-tree/marked-result-value)
                         (lens/pattern [edit-tree/literal-decimal-value
                                        edit-tree/edit-tree-focused?]))
          latitude-value-lens (lens/>> latitude-lens lens/first)
          longitude-lens (lens/>>
                          (lens/>>
                           (lens/member "http://schema.org/longitude")
                           lens/first
                           edit-tree/marked-result-value)
                          (lens/pattern [edit-tree/literal-decimal-value
                                         edit-tree/edit-tree-focused?]))
          longitude-value-lens (lens/>> longitude-lens lens/first)
          lat (js/parseFloat (latitude-value-lens eprops))
          long (js/parseFloat (longitude-value-lens eprops))
          coords [lat long]]

      (dom/div
       {:style {:border "1px solid gray"
                :border-radius "3px"
                :display "flex"
                :flex-direction "column"}}
       (-> (c/isolate-state
            (view-box-around coords)
            (leaflet/main {:style {:height 200
                                   :min-width 400}}
                          [(leaflet/make-pin
                            "X"
                            120 ;; green
                            coords)]))
           (c/handle-action (fn [eprops ac]
                              (if (is-a? leaflet/click-action ac)
                                (let [[lat lng] (leaflet/click-action-coordinates ac)]
                                  (-> eprops
                                      (latitude-value-lens (str lat))
                                      (longitude-value-lens (str lng))))
                                (c/return :action ac)))))
       (dom/div
        (context/text ctx tr/latitude-label)
        (c/focus latitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")}))
        (context/text ctx tr/longitude-label)
        (c/focus longitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")})))))))

(c/defn-item ^:private postal-address-component [ctx editable? force-editing?]
  (c/with-state-as eprops
    (let [unpack-value (lens/>> lens/first
                                edit-tree/marked-result-value
                                edit-tree/literal-string-value)
          unpack-focus (lens/>> lens/first
                                edit-tree/marked-result-value
                                edit-tree/edit-tree-focused?)
          unpack (lens/pattern [unpack-value unpack-focus])

          street-address-lens (lens/>>
                               (lens/member "http://schema.org/streetAddress")
                               unpack)
          postal-code-lens (lens/>>
                            (lens/member "http://schema.org/postalCode")
                            unpack)
          address-locality-lens (lens/>>
                                 (lens/member "http://schema.org/addressLocality")
                                 unpack)
          address-country-lens (lens/>>
                                (lens/member "http://schema.org/addressCountry")
                                unpack)]
      (dom/div
       {:style {:display "flex"
                :flex-direction "column"
                :gap "1ex"
                :border "1px solid gray"
                :border-radius "3px"
                :padding "1ex 1em"}}
       (dom/div
        (dom/label "Street address"
                   (dom/br)
                   (c/focus street-address-lens
                            (ds/input+focus
                             {:disabled (when-not force-editing? "disabled")}))))

       (dom/div
        (dom/label "Postal code"
                   (dom/br)
                   (c/focus postal-code-lens
                            (ds/input+focus
                             {:disabled (when-not force-editing? "disabled")}))))

       (dom/div
        (dom/label "Locality (Town)"
                   (dom/br)
                   (c/focus address-locality-lens
                            (ds/input+focus
                             {:disabled (when-not force-editing? "disabled")}))))

       (dom/div
        (dom/label "Country"
                   (dom/br)
                   (c/focus address-country-lens
                            (ds/input+focus
                             {:disabled (when-not force-editing? "disabled")}))))))))

(declare day-of-week-component)

(c/defn-item ^:private opening-hours-specification-component [ctx editable? force-editing?]
  (c/with-state-as eprops
    (let [unpack (lens/>> lens/first
                          edit-tree/marked-result-value
                          edit-tree/literal-string-value)
          day-of-week-lens (lens/>>
                            (lens/member "http://schema.org/dayOfWeek")
                            lens/first
                            edit-tree/marked-result-value)
          opens-lens (lens/>>
                      (lens/member "http://schema.org/opens")
                      unpack)
          closes-lens (lens/>>
                       (lens/member "http://schema.org/closes")
                       unpack)]
      (dom/div
       {:style {:display "flex"
                :gap "1ex"
                :border "1px solid gray"
                :border-radius "3px"
                :padding "1ex 1em"}}
       (dom/div
        (dom/label "Day"
                   (dom/br)
                   (c/focus day-of-week-lens
                            (day-of-week-component ctx editable? force-editing?))))

       (dom/div
        (dom/label "Opens"
                   (dom/br)
                   (c/focus opens-lens
                            (ds/input
                             {:type "time"
                              :disabled (when-not editable? "disabled")}))))

       (dom/div
        (dom/label "Closes"
                   (dom/br)
                   (c/focus closes-lens
                            (ds/input
                             {:type "time"
                              :disabled (when-not editable? "disabled")}))))))))

(c/defn-item ^:private day-of-week-component [ctx editable? force-editing?]
  (c/focus (lens/pattern [edit-tree/tree-uri
                          edit-tree/edit-tree-focused?])
           (ds/select+focus
            {:disabled (when-not force-editing? "disabled")
             :style {:padding "7px 8px"}}
            (forms/option {:value "http://schema.org/Monday"} "Monday")
            (forms/option {:value "http://schema.org/Tuesday"} "Tuesday")
            (forms/option {:value "http://schema.org/Wednesday"} "Wednesday")
            (forms/option {:value "http://schema.org/Thursday"} "Thursday")
            (forms/option {:value "http://schema.org/Friday"} "Friday")
            (forms/option {:value "http://schema.org/Saturday"} "Saturday")
            (forms/option {:value "http://schema.org/Sunday"} "Sunday"))))

(c/defn-item ^:private node-component-for-type [type ctx types editable? force-editing? background-color compare-edit-tree adornment]
  (c/with-state-as enode
    (let [type-uri (tree/type-uri type)]
      (cond
        (and (= type-uri "http://schema.org/GeoCoordinates")
             (edit-node-is-geo-coordinates-value? enode))
        (dom/div
         {:id (edit-tree/edit-node-uri enode)}
         (value-node/as-value-node
          (geo-coordinates-component ctx editable? force-editing?)))

        #_#_(and (= type-uri "http://schema.org/PostalAddress")
             (edit-node-is-postal-address-value? enode))
        (dom/div
         {:id (edit-tree/edit-node-uri enode)}
         (value-node/as-value-node
          (postal-address-component schema editable? force-editing?)))

        #_#_(and (= type-uri "http://schema.org/OpeningHoursSpecification")
                 (edit-node-is-opening-hours-specification-value? enode))
        (value-node/as-value-node
         (opening-hours-specification-component schema editable? force-editing?))

        :else
        (node-component ctx types editable? force-editing? background-color compare-edit-tree adornment)))))

(defn- index-of [v elem]
  (some (fn [[i x]]
          (when (= x elem)
            i))
        (map-indexed vector v)))



(c/defn-item edit-tree-component* [ctx types editable? force-editing? & [background-color compare-edit-tree adornment]]
  (c/with-state-as etree
    (let [bgc (or background-color "#eee")]
      (cond
        (edit-tree/literal-string? etree)
        (c/focus (lens/pattern [edit-tree/literal-string-value
                                edit-tree/literal-string-focused?])
                 (ds/input+focus {:disabled (when-not force-editing?
                                              "disabled")}))

        (edit-tree/literal-decimal? etree)
        (c/focus (lens/pattern [edit-tree/literal-decimal-value
                                edit-tree/literal-decimal-focused?])
                 (ds/input+focus {:type "decimal"
                                  :disabled (when-not force-editing?
                                              "disabled")}))

        (edit-tree/literal-boolean? etree)
        (c/focus edit-tree/literal-boolean-value
                 (dom/div
                  {:style {:display "flex"
                           :align-items "center"
                           :gap "0.5em"
                           :height "100%"}}
                  (ds/input {:type "checkbox"
                             :style {:width "20px"
                                     :height "20px"
                                     :margin "0"}
                             :disabled (when-not force-editing?
                                         "disabled")})
                  (dom/div
                   (c/dynamic (partial pr-boolean ctx)))))

        (edit-tree/literal-time? etree)
        (c/focus (lens/pattern [edit-tree/literal-time-value
                                edit-tree/literal-time-focused?])
                 (ds/input+focus {:type "time"
                                  :disabled (when-not force-editing?
                                              "disabled")}))

        (edit-tree/literal-date? etree)
        (c/focus (lens/pattern [edit-tree/literal-date-value
                                edit-tree/literal-date-focused?])
                 (ds/input+focus {:type "date"
                                  :disabled (when-not force-editing?
                                              "disabled")}))

        (edit-tree/ref? etree)
        (let [uri (edit-tree/ref-uri etree)]
          (dom/div
           {:style {:display "flex"
                    :gap "1em"
                    :align-items "center"}}
           (the-circle)
           (dom/b "REF")
           (dom/a {:href (str "#" (tree/uri-string uri))}
                  (pr-uri uri))))

        (edit-tree/many? etree)
        (c/focus edit-tree/many-edit-trees
                 (c/with-state-as etrees
                   (if (empty? etrees)
                     (dom/div
                      {:style {:font-style "italic"
                               :color "gray"}}
                      (context/text ctx tr/nothing-to-display))
                     (apply
                      dom/div
                      {:style {:display "flex"
                               :flex-direction "column"
                               :gap "4ex"}}
                      (map
                       (fn [[idx etree]]
                         (c/focus (lens/at-index idx)
                                  (edit-tree-component* ctx types editable? force-editing? bgc compare-edit-tree adornment)))
                       (sort-by second
                                (comp - compare-edit-tree)
                                (map vector (range) etrees)))))))

        (edit-tree/exists? etree)
        (c/focus edit-tree/exists-edit-tree
                 (edit-tree-component* ctx types editable? force-editing? bgc compare-edit-tree adornment))

        (edit-tree/edit-node? etree)
        (node-component-for-type (edit-tree/edit-node-type etree)
                                 ctx
                                 types
                                 editable?
                                 force-editing?
                                 bgc
                                 compare-edit-tree
                                 adornment)))))

(defn- make-compare-with-uri-order [uri-order]
  (let [get-position (fn [etree]
                       (cond
                         (edit-tree/edit-node? etree)
                         (index-of
                          uri-order
                          (edit-tree/edit-node-uri etree))

                         (edit-tree/ref? etree)
                         (index-of
                          uri-order
                          (edit-tree/ref-uri etree))))]
    (fn [etree-1 etree-2]
      (let [pos-1 (get-position etree-1)
            pos-2 (get-position etree-2)]
        (cond
          (and (nil? pos-1)
               (nil? pos-2))
          ;; fallback
          (edit-tree/compare-edit-tree etree-1 etree-2)

          (nil? pos-1)
          -1

          (nil? pos-2)
          +1

          (<= pos-1 pos-2)
          +1

          (> pos-1 pos-2)
          -1

          :else
          0)))))

(c/defn-item edit-tree-component [ctx types editable? force-editing? & [background-color uri-order adornment]]
  (-> (edit-tree-component* ctx
                            types
                            editable?
                            force-editing?
                            background-color
                            (make-compare-with-uri-order uri-order)
                            adornment)
      (c/handle-action (fn [etree action]
                         (if (is-a? set-reference-action action)
                           (edit-tree/set-reference etree
                                                    (set-reference-action-subject-uri action)
                                                    (set-reference-action-predicate action)
                                                    (set-reference-action-old-uri action)
                                                    (set-reference-action-new-uri action))
                           ;; else
                           (c/return :action action))))))

(c/defn-item edit-graph [ctx editable? force-editing? graph & [background-color]]
  (c/isolate-state (edit-tree/graph->edit-tree graph)
                   (edit-tree-component ctx nil editable? force-editing? background-color)))

(c/defn-item readonly-graph [ctx graph & [background-color]]
  (c/isolate-state (edit-tree/graph->edit-tree graph)
                   (edit-tree-component ctx nil false false background-color)))

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
            [clojure.string :as string]))

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

(defn- pr-boolean [on?]
  (if on?
    "True"
    "False"))

(c/defn-item ^:private before-after [before-item after-item background-color]
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
                        "Before")
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
                        "After")))

     (dom/div
      {:style {:border "1px solid gray"
               :padding "1ex 1em"}}
      (c/focus lens/first
               (case before-or-after
                 ::before
                 before-item

                 ::after
                 after-item))))))

(defn- label-for-kind [kind]
  (cond
    (= kind tree/literal-string)
    "String"

    (= kind tree/literal-decimal)
    "Decimal"

    (= kind tree/literal-boolean)
    "Boolean"

    (= kind tree/literal-time)
    "Time"

    (= kind tree/literal-date)
    "Date"

    (= kind tree/ref)
    "Node"

    (= kind tree/node)
    "Node"))

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

(c/defn-item ^:private component-for-predicate [predicate schema types editable? editing? background-color compare-edit-tree adornment]
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
                                   (schema/label-for-type schema type-uri)))
                     (sort
                      (distinct
                       (conj types
                             (edit-tree/tree-uri etree)))))))

      (and (= predicate "http://schema.org/dayOfWeek")
           (or (edit-tree/edit-node? etree)
               (edit-tree/ref? etree)))
      (day-of-week-component schema editable? editing?)

      (= predicate "http://schema.org/name")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/input+focus {:disabled (when-not editing? "disabled")
                                :style {:font-size "2em"}}))

      (= predicate "http://schema.org/description")
      (c/focus (lens/pattern [edit-tree/literal-string-value
                              edit-tree/edit-tree-focused?])
               (ds/textarea+focus {:style {:width "100%"
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
      (day-of-week-component schema editable? editing?)

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
                (forms/option {:value "elderly"} "Elderly")
                (forms/option {:value "queer"} "Queer")
                (forms/option {:value "immigrants"} "Immigrants")))

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
                            (forms/option {:value kind} (label-for-kind kind)))
                          (distinct
                           (conj (schema/kinds-for-predicate schema predicate)
                                 kind)))))))
       (edit-tree-component*
        schema
        (schema/types-for-predicate schema predicate)
        editable?
        editing?
        background-color
        compare-edit-tree
        adornment)))))

(c/defn-item add-property-button [schema predicates]
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
                       (forms/option {:value pred} (schema/label-for-predicate schema pred)))
                     predicates)))

      (ds/button-primary
       {:style {:padding "2px 12px"
                :color "#444"
                :background "#ddd"
                :font-weight "normal"}
        :onClick
        (fn [[node predicate] _]
          [(edit-tree/edit-node-add-property node predicate (default/default-tree-for-predicate schema predicate))
           predicate])}
       "Add property")))))

;; OSM

(defn- pr-osm-uri [uri]
  (dom/a {:href uri}
         "View on OpenStreetMap"))

(declare readonly-graph)

(c/defn-item osm-importer [schema osm-uri]
  (c/with-state-as graph
    (c/fragment

     (when (some? osm-uri)
       (util/load-json-ld-state
        (osm/osm-lookup-request osm-uri)))

     (when graph (readonly-graph schema graph)))))

(c/defn-item link-organization-with-osm-button [schema osm-uri close-action]
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
               (osm-importer schema (:commit-osm-uri local-state))))

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

(defn- ask-ai [schema close-action]
  (dom/div
   {:style {:display "flex"
            :flex-direction "column"
            :overflow "auto"}}

   (modal/padded
    {:style {:overflow "auto"}}
    (ask-ai/main schema readonly-graph close-action))

   (modal/toolbar
    (ds/button-secondary {:onClick #(c/return :action close-action)}
                         "Cancel"))))

(defn- set-properties [schema]
  (c/with-state-as node
    (dom/div {:style {:display "flex"
                      :gap "2em"
                      :align-items "center"}}

             (dom/div
              {:style {:display "flex"}}
              (add-property-button schema (schema/predicates-for-type schema (edit-tree/node-type node))))

             (dom/div
              {:style {:display "flex"
                       :gap "1em"}}

              (when (edit-tree/organization? node)
                (if-let [osm-uri (osm/node-osm-uri node)]

                  (dom/div
                   {:style {:display "flex"
                            :gap "1em"}}
                   (pr-osm-uri osm-uri)
                   (modal/modal-button "Update" #(link-organization-with-osm-button schema osm-uri %)))
                  (modal/modal-button "Link with OpenStreetMap" #(link-organization-with-osm-button schema nil %))))

              (modal/modal-button (ds/lightbulb-icon "21") (partial ask-ai schema))))))

(defn- valid-uri? [x]
  (and (string? x)
       (re-find #"https?://[a-zA-z0-9]+" x)))

(defn- set-reference [close-action]
  (c/with-state-as node
    (c/local-state
     (let [uri (edit-tree/tree-uri node)]
       [uri (wisen.frontend.forms/make-selected 0 (count (str uri)))])
     (dom/div
      (modal/padded
       (dom/h3 "Set as reference to another node")

       (c/focus lens/second (ds/input+focus {:size 80})))

      (modal/toolbar
       (ds/button-secondary {:onClick #(c/return :action close-action)}
                            "Cancel")
       (c/with-state-as state
         (let [text (first (second state))]

           (ds/button-primary {:onClick
                               (fn [[node [new-uri _]]]
                                 (let [old-uri (edit-tree/tree-uri node)]
                                   (c/return :action (set-reference-action
                                                      set-reference-action-old-uri old-uri
                                                      set-reference-action-new-uri (or
                                                                                    (existential/string->existential new-uri)

                                                                                    new-uri))
                                             :action close-action)))}
                              "Set reference"))))))))

(defn- refresh-node-request [uri]
  (ajax/GET uri
            {:headers {"accept" "application/ld+json"}}))

(c/defn-item ^:private refresh-node [uri]
  (util/load-json-ld
   (refresh-node-request uri)))

(defn- refresh-button []
  (c/with-state-as [node refresh-state :local ::idle]
    (case refresh-state
      ::idle
      (c/focus lens/second
               (ds/button-primary {:onClick #(c/return :state ::run)}
                                  "Refresh"))

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
                "Refresh failed"
                " "
                (c/focus lens/second
                         (ds/button-primary {:onClick #(c/return :state ::run)}
                                            "(Retry)"))))))

(defn- the-circle [& children]
  (apply dom/div
         {:style {:border "1px solid #777"
                    :background "white"
                    :border-radius "100%"
                    :width "27px"
                  :height "27px"}}
         children))

(c/defn-item property-object-component [schema types predicate editable? force-editing? last-property? background-color compare-edit-tree adornment]
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
                                                #_#_:gap "1em"
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
                                         (schema/label-for-predicate schema predicate))

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
                                                 (component-for-predicate predicate schema types false false background-color compare-edit-tree adornment))

                                        (edit-tree/added? marked-edit-tree)
                                        (c/focus edit-tree/added-result-value
                                                 (component-for-predicate predicate schema types editable? force-editing? background-color compare-edit-tree adornment))

                                        (edit-tree/same? marked-edit-tree)
                                        (c/focus edit-tree/maybe-changed-result-value
                                                 (component-for-predicate predicate schema types editable? force-editing? background-color compare-edit-tree adornment))

                                        (edit-tree/changed? marked-edit-tree)
                                        (before-after
                                         ;; before
                                         (c/focus edit-tree/maybe-changed-original-value
                                                  (component-for-predicate predicate schema types false false background-color compare-edit-tree adornment))
                                         ;; after
                                         (c/focus edit-tree/maybe-changed-result-value
                                                  (component-for-predicate predicate schema types editable? force-editing? background-color compare-edit-tree adornment))

                                         background-color))))))

                        marked-edit-trees))))

(c/defn-item properties-component [schema types editable? force-editing? background-color compare-edit-tree adornment]
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
                                     (property-object-component schema types predicate editable? force-editing? last? background-color compare-edit-tree adornment)))
                          (c/handle-action
                           (fn [_ action]
                             (if (and (is-a? set-reference-action action)
                                      (nil? (set-reference-action-predicate action)))
                               (c/return :action (set-reference-action-predicate action predicate))
                               (c/return :action action))))))

                    ks)))))

(c/defn-item ^:private references-indicator [id]
  (c/isolate-state
   nil
   (c/with-state-as result
     (cond
       (nil? result)
       (c/fragment
        (spinner/main "Determining references")
        (ajax/fetch (ajax/GET (str "/api/references/" id))))

       (and (ajax/response? result)
            (ajax/response-ok? result))
       (let [n (count (ajax/response-value result))]
         (dom/i
          (str n)
          " "
          (if (= 1 n)
            "reference"
            "references")
          " "
          "in total"))))))

(c/defn-effect copy-to-clipboard! [s]
  (.writeText (.-clipboard js/navigator) s))

(defn- node-component [schema types editable? force-editing? background-color compare-edit-tree adornment]
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
                    {:id (tree/uri-string uri)
                     :href (tree/uri-string uri)}
                    (pr-uri uri)))

                 (when (or (not (existential/existential? uri))
                           force-editing?)
                   (ds/button-primary
                    {:onClick #(c/return :action (copy-to-clipboard! uri))}
                    "Copy"))

                 (when editing?
                   (c/fragment
                    (c/focus lens/first
                             (modal/modal-button "Set reference" set-reference)))))

        (when (edit-tree/can-refresh? node)
          (c/focus lens/first (refresh-button)))

        (when editable?
          (c/focus lens/second
                   (ds/button-primary {:onClick not}
                                      (if editing? "Done" "Edit"))))

        (when (wisen-uri/is-wisen-uri? uri)
          (references-indicator (wisen-uri/wisen-uri-id uri))))

       (c/focus
        lens/first
        (dom/div

         (-> (c/focus edit-tree/edit-node-properties
                      (c/with-state-as eprops
                        (when-not (empty? eprops)
                          (dom/div
                           {:style {:display "flex"
                                    :flex-direction "column"
                                    :gap "2ex"
                                    :margin-left "14px"
                                    :padding-top "12px"
                                    :border-left "1px solid gray"
                                    :padding-bottom "2ex"}}

                           (properties-component schema types editable? editing? background-color compare-edit-tree adornment)))))
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
           (set-properties schema))))))))

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

(c/defn-item ^:private geo-coordinates-component [schema editable? force-editing?]
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
        "Latitude:"
        (c/focus latitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")}))
        "Longitude:"
        (c/focus longitude-lens
                 (ds/input+focus {:disabled (when-not editable? "disabled")})))))))

(c/defn-item ^:private postal-address-component [schema editable? force-editing?]
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

(c/defn-item ^:private opening-hours-specification-component [schema editable? force-editing?]
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
                            (day-of-week-component schema editable? force-editing?))))

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

(c/defn-item ^:private day-of-week-component [schema editable? force-editing?]
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

(c/defn-item ^:private node-component-for-type [type schema types editable? force-editing? background-color compare-edit-tree adornment]
  (c/with-state-as enode
    (let [type-uri (tree/type-uri type)]
      (cond
        (and (= type-uri "http://schema.org/GeoCoordinates")
             (edit-node-is-geo-coordinates-value? enode))
        (dom/div
         {:id (edit-tree/edit-node-uri enode)}
         (value-node/as-value-node
          (geo-coordinates-component schema editable? force-editing?)))

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
        (node-component schema types editable? force-editing? background-color compare-edit-tree adornment)))))

(defn- index-of [v elem]
  (some (fn [[i x]]
          (when (= x elem)
            i))
        (map-indexed vector v)))



(c/defn-item edit-tree-component* [schema types editable? force-editing? & [background-color compare-edit-tree adornment]]
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
                   (c/dynamic pr-boolean))))

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
                      "Nothing to display")
                     (apply
                      dom/div
                      {:style {:display "flex"
                               :flex-direction "column"
                               :gap "4ex"}}
                      (map
                       (fn [[idx etree]]
                         (c/focus (lens/at-index idx)
                                  (edit-tree-component* schema types editable? force-editing? bgc compare-edit-tree adornment)))
                       (sort-by second
                                (comp - compare-edit-tree)
                                (map vector (range) etrees)))))))

        (edit-tree/exists? etree)
        (c/focus edit-tree/exists-edit-tree
                 (edit-tree-component* schema types editable? force-editing? bgc compare-edit-tree adornment))

        (edit-tree/edit-node? etree)
        (node-component-for-type (edit-tree/edit-node-type etree)
                                 schema
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
          (compare etree-1 etree-2)

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

(c/defn-item edit-tree-component [schema types editable? force-editing? & [background-color uri-order adornment]]
  (-> (edit-tree-component* schema
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

(c/defn-item edit-graph [schema editable? force-editing? graph & [background-color]]
  (c/isolate-state (edit-tree/graph->edit-tree graph)
                   (edit-tree-component schema nil editable? force-editing? background-color)))

(c/defn-item readonly-graph [schema graph & [background-color]]
  (c/isolate-state (edit-tree/graph->edit-tree graph)
                   (edit-tree-component schema nil false false background-color)))

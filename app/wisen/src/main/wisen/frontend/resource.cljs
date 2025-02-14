(ns wisen.frontend.resource
  (:refer-clojure :exclude [dissoc assoc type])
  (:require [active.data.record :as record :refer-macros [def-record]]
            [active.data.realm :as r]
            [active.clojure.lens :as lens]))

;; Linkworker-AirTable columns and their mappings to schema.org vocabulary:

;; Organisation/Name
;; name

;; Bezirk
;; areaServed ?

;; Zielgruppe
;; ??

;; Zielgruppenspezifisch...
;; ?

;; Angebot Kategorie
;; keywords

;; Aktivitätsbeschreibung
;; description

;; Ort & Erreichbarkeit
;; location

;; Wartezeit
;; ??

;; Teilnahmebedingungen
;; ??

;; AnsprechpartnerIn
;; email, telephone, contactPoint(s)

;; Persönlicher Kontakt
;; email, telephone, contactPoint(s)

;; Öffnungszeiten
;; openingHours

;; Barrierefreiheit
;; <none> (accessibilityFeature ist für elektronische Medien)

;; Link
;; url

;; Auf Freiwilligensuche
;; unklar, vielleicht via (makesOffer ->) JobPosting -> employmentUnit

;; Some predicates

(def id "@id")

(def type "@type")

;; ---

(defn literal-string [x]
  x)

(defn literal-string? [x]
  (string? x))

(def literal-string-value lens/id)

(declare resource)

(def-record property
  [property-predicate :- (r/union r/string
                                  (r/enum id))

   property-object :- (r/union r/string
                               (r/delay resource))])

(defn prop [p o]
  (property
   property-predicate p
   property-object o))

(defn property? [x]
  (record/is-a? property x))

(def-record resource
  [resource-properties :- (r/sequence-of property)])

(defn res [& props]
  (resource resource-properties props))

(defn resource? [x]
  (record/is-a? resource x))

(defn lookup [pred]
  (fn
    ([r]
     (some (fn [prop]
             (when (= pred (property-predicate prop))
               (property-object prop)))
           (resource-properties r)))
    ([r new-object]
     (lens/overhaul r resource-properties
                    (fn [old-properties]
                      (map (fn [property]
                             (if (= pred (property-predicate property))
                               (prop pred new-object)
                               property))
                           old-properties))))))

(defn assoc [r pred value]
  (resource
   resource-properties
   (concat (resource-properties r)
           [(prop pred value)])))

(defn dissoc [pred]
  (fn
    ([r]
     (resource
      resource-properties
      (remove #(= pred (property-predicate %))
              (resource-properties r))))

    ([old-resource new-resource]
     (apply
      res
      (conj
       (resource-properties new-resource)
       (prop pred ((lookup pred) old-resource)))))))

(def string-literal-kind ::string)
(def resource-literal-kind ::resource)

(defn default-value-for-kind [kind]
  (cond
     (= kind string-literal-kind)
     ""

     (= kind resource-literal-kind)
     (res (prop "predicate" "object"))))

(defn kind
  ([value]
   (cond
     (literal-string? value)
     string-literal-kind

     (resource? value)
     resource-literal-kind))
  ([value new-kind]
   (if (= (kind value) new-kind)
     ;; keep as-is
     value
     ;; change to new default
     (default-value-for-kind new-kind))))

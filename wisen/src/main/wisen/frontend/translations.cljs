(ns wisen.frontend.translations
  (:require
   [active.data.realm :as realm]
   [wisen.common.lang :refer-macros [define-text]]))

(def de "de")
(def en "en")

(defn- parse-bcp47 [s]
  (.-language (js/Intl.Locale. s)))

(defn initial-language! []
  (parse-bcp47
   (.-language js/navigator)))

(def language
  (realm/enum
   de
   en))

(define-text search
  de "Suche"
  en "Search")

(define-text results
  de "Ergebnisse"
  en "Results")

(define-text organization
  de "Organisation"
  en "Organization")

(define-text event
  de "Veranstaltung"
  en "Event")

(define-text offer
  de "Angebot"
  en "Offer")

(define-text copy
  de "Kopieren"
  en "Copy")

(define-text edit
  de "Bearbeiten"
  en "Edit")

(define-text rdf
  de "RDF"
  en "RDF")

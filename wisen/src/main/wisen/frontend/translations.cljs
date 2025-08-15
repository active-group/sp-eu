(ns wisen.frontend.translations
  (:refer-clojure :exclude [time update])
  (:require
   [active.data.realm :as realm]
   [wisen.common.lang :refer-macros [define-text define-text-function]]))

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

(define-text search-everything
  de "Komplettsuche"
  en "Search everything")

(define-text results
  de "Ergebnisse"
  en "Results")

(define-text-function results-for [s]
  de (str "Ergebnisse für »" s "«")
  en (str "Results for «" s "»"))

(define-text no-results-yet
  de "Keine Ergebnisse bisher"
  en "No results yet")

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

(define-text additions
  de "Ergänzungen"
  en "Additions")

(define-text deletions
  de "Löschungen"
  en "Deletions")

(define-text commit-changes
  de "Änderungen speichern"
  en "Commit changes")

(define-text committing
  de "Speichern …"
  en "Committing …")

(define-text commit-successful
  de "Speichern erfolgreich!"
  en "Commit successful!")

(define-text commit-failed
  de "Speichern fehlgeschlagen!"
  en "Commit failed!")

(define-text close
  de "Schließen"
  en "Close")

(define-text searching
  de "Suche …"
  en "Searching …")

(define-text is-true
  de "Ja"
  en "True")

(define-text is-false
  de "Nein"
  en "False")

(define-text before
  de "Vorher"
  en "Before")

(define-text after
  de "Nachher"
  en "After")

(define-text string
  de "Text"
  en "Text")

(define-text decimal
  de "Zahl"
  en "Number")

(define-text bool
  de "Ja/Nein"
  en "Yes/No")

(define-text time
  de "Uhrzeit"
  en "Time")

(define-text date
  de "Datum"
  en "Date")

(define-text node
  de "Knoten"
  en "Node")

(define-text elderly
  de "Ältere"
  en "Elderly")

(define-text queer
  de "Queer"
  en "Queer")

(define-text immigrants
  de "Migrationshintergrund"
  en "Immigrants")

(define-text add-property
  de "Eigenschaft hinzufügen"
  en "Add property")

(define-text view-on-open-street-map
  de "Auf OpenStreetMap anschauen"
  en "View on OpenStreetMap")

(define-text link-with-open-street-map
  de "Mit OpenStreetMap verknüpfen"
  en "Link with OpenStreetMap")

(define-text update
  de "Anpassen"
  en "Update")

(define-text set-as-reference-to-another-node
  de "Eine Referenz setzen"
  en "Set as reference to another node")

(define-text cancel
  de "Abbrechen"
  en "Cancel")

(define-text set-reference
  de "Referenz setzen"
  en "Set reference")

(define-text refresh
  de "Neu laden"
  en "Refresh")

(define-text refresh-failed
  de "Fehler beim Laden"
  en "Refresh failed")

(define-text retry-refresh
  de "(Wiederholen)"
  en "(Retry)")

(define-text determining-references
  de "Lade Referenzen …"
  en "Determining references …")

(define-text-function n-references-in-total [n]
  de (if (= 1 n)
       "1 Referenz insgesamt"
       (str n " Referenzen insgesamt"))
  en (if (= 1 n)
       "1 reference in total"
       (str n " references in total")))

(define-text no-references
  de "Keine Referenzen"
  en "No references")

(define-text done
  de "Fertig"
  en "Done")

(define-text latitude-label
  de "Breitengrad:"
  en "Latitude:")

(define-text longitude-label
  de "Längengrad:"
  en "Longitude:")

(define-text nothing-to-display
  de "Nichts anzuzeigen"
  en "Nothing to display")

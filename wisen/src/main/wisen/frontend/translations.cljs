(ns wisen.frontend.translations
  (:refer-clojure :exclude [time update])
  (:require
   [active.data.realm :as realm]
   [wisen.common.lang :refer-macros [text define-text define-text-function]]))

(def de "de")
(def en "en")
(def fr "fr")

(defn- parse-bcp47 [s]
  (.-language (js/Intl.Locale. s)))

(defn initial-language! []
  (parse-bcp47
   (.-language js/navigator)))

(def language
  (realm/enum
   de
   en
   fr))

(define-text search
  de "Suche"
  en "Search"
  fr "Chercher")

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

(define-text pager-show-results
  de "Zeige"
  en "Show")

(define-text organization
  de "Organisation"
  en "Organization")

(define-text event
  de "Veranstaltung"
  en "Event")

(define-text person
  de "Person"
  en "Person")

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

(define-text no-properties
  de "Keine Eigenschaften"
  en "No properties")

(define-text rdf-import-title
  de "RDF-Import"
  en "RDF Import")

(define-text rdf-import-how-to-use
  de "Was kann ich hier machen?"
  en "How to use")

(define-text rdf-import-prolog
  de "Mit diesem Tool können Sie beliebige RDF-Daten in den Datenspeicher hinzufügen. Die Daten müssen im JSON-LD-Format vorliegen. Idealerweise sollten die Prädikate und Typen aus dem schema.org-Vokabular stammen. Sie können dieses Tool beispielsweise verwenden, indem Sie einen LLM-Dienst wie ChatGPT oder Claude bitten, bestimmte Informationen in RDF wie folgt zu übersetzen:"
  en "With this tool you can add arbitrary RDF data to the data store. The data must be formatted in the JSON-LD format. Ideally the predicates and types should be from the schema.org vocabulary. You can use this tool e.g. by asking an LLM service such as ChatGPT or Claude to translate some information into RDF like this:")

(define-text rdf-import-sample-prompt
  de "Translate the information on this website (<some website you want to process>) to JSON-LD using the schema.org vocabulary. Don't make up any information (such as descriptions, phone numbers, etc.) yourself."
  en "Translate the information on this website (<some website you want to process>) to JSON-LD using the schema.org vocabulary. Don't make up any information (such as descriptions, phone numbers, etc.) yourself.")

(define-text rdf-import-epilog
  de "Ihr LLM wird Ihnen dann wahrscheinlich einen Codeblock präsentieren, den Sie in das linke Textfeld unten kopieren und einfügen können."
  en "Your LLM will then probably present you a code block that you can copy and paste into the left textarea below.")

;; RDF predicates

(def predicates
  {

   "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
   (text
    de "Typ"
    en "Type"
    "<type>")

   "http://schema.org/name"
   (text
    de "Name"
    en "Name"
    "<name>")

   "http://schema.org/description"
   (text
    en "Description"
    de "Beschreibung"
    "<description>")

   "http://schema.org/keywords"
   (text
    en "Tags"
    de "Stichwörter"
    "<tags>")

   "http://schema.org/location"
   (text
    en "Location"
    de "Standort"
    "<location>")

   "http://schema.org/openingHoursSpecification"
   (text
    en "Opening Hours"
    de "Öffnungszeiten"
    "<opening hours>")

   "http://schema.org/url"
   (text
    en "Website"
    de "Website"
    "<url>")

   "http://schema.org/address"
   (text
    en "Address"
    de "Adresse"
    "<address>")

   "http://schema.org/streetAddress"
   (text
    en "Street + Nr"
    de "Straße + Hausnummer"
    "<streetAddress>")

   "http://schema.org/postalCode"
   (text
    en "Postal code"
    de "Postleitzahl"
    "<postalCode>")

   "http://schema.org/addressLocality"
   (text
    en "Town/region"
    de "Stadt/Region"
    "<addressLocality>")

   "http://schema.org/addressCountry"
   (text
    en "Country code"
    de "Land"
    "<addressCountry>")

   "http://schema.org/addressRegion"
   (text
    en "Region"
    de "Region"
    "<addressRegion>")

   "http://schema.org/dayOfWeek"
   (text
    en "Day of week"
    de "Wochentag"
    "<dayOfWeek>")

   "http://schema.org/opens"
   (text
    en "Opens"
    de "Öffnet"
    "<opens>")

   "http://schema.org/closes"
   (text
    en "Closes"
    de "Schließt"
    "<closes>")

   "http://www.w3.org/2003/01/geo/wgs84_pos#long"
   (text
    en "Longitude"
    de "Längengrad"
    "<longitude>")

   "http://www.w3.org/2003/01/geo/wgs84_pos#lat"
   (text
    en "Latitude"
    de "Breitengrad"
    "<latitude>")

   "http://schema.org/contactPoint"
   (text
    en "Contact point"
    de "Kontaktpunkt"
    "<contactPoint>")

   "http://schema.org/eventSchedule"
   (text
    en "Event schedule"
    de "Zeitplan"
    "<eventSchedule>")

   "http://schema.org/email"
   (text
    en "Email"
    de "Email"
    "<email>")

   "http://schema.org/telephone"
   (text
    en "Telephone"
    de "Telefon"
    "<telephone>")

   "http://schema.org/byDay"
   (text
    en "Day"
    de "Tag"
    "<byDay>")

   "http://schema.org/startTime"
   (text
    en "Start time"
    de "Startzeitpunkt"
    "<startTime>")

   "http://schema.org/endTime"
   (text
    en "End time"
    de "Endzeitpunkt"
    "<endTime>")

   "http://schema.org/repeatFrequency"
   (text
    en "Repeat frequency"
    de "Wiederholungsrate"
    "<repeatFrequency>")

   "http://schema.org/contactType"
   (text
    en "Contact type"
    de "Art des Kontakts"
    "<contactType>")

   "http://schema.org/audience"
   (text
    en "Audience"
    de "Zielgruppe"
    "<audience>")

   "http://schema.org/audienceType"
   (text
    en "Audience type"
    de "Art der Zielgruppe"
    "<audienceType>")

   "http://schema.org/accessibilityFeature"
   (text
    en "Accessibility feature"
    de "Barrierefreiheitsfunktion"
    "<accessibilityFeature>")

   "http://schema.org/geographicArea"
   (text
    en "Geographic area"
    de "Geographischer Bereich"
    "<geographicArea>")

   "http://schema.org/courseMode"
   (text
    en "Course mode"
    de "Kursmodus")

   "http://schema.org/coursePrerequisites"
   (text
    en "Prerequisites"
    de "Voraussetzungen")

   "http://schema.org/hasCourseInstance"
   (text
    en "Course instance"
    de "Kursinstanz")

   "http://schema.org/startDate"
   (text
    en "Start"
    de "Beginn")

   "http://schema.org/endDate"
   (text
    en "End"
    de "Ende")

   "http://schema.org/maximumAttendeeCapacity"
   (text
    en "Maximum attendees"
    de "Maximale Teilnehmer:innenzahl")

   "http://schema.org/instructor"
   (text
    en "Instructor"
    de "Kursleiter")

   "http://schema.org/offers"
   (text
    en "Offers"
    de "Angebote")

   "http://schema.org/eventAttendanceMode"
   (text
    en "Attendance mode"
    de "Modus")

   "http://schema.org/performer"
   (text
    en "Performer"
    de "Ausführende(r)")

   "http://schema.org/logo"
   (text
    en "Logo"
    de "Logo")

   "http://schema.org/sameAs"
   (text
    en "Same as"
    de "Identisch mit")
   })

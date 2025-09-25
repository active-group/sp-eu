(ns wisen.backend.decorator
  (:require [wisen.backend.osm :as osm]
            [wisen.backend.search :as search])
  (:import
   (org.apache.jena.rdf.model Model)
   (org.apache.jena.datatypes.xsd XSDDatatype)))

(defn decorate-geo! [^Model base-model]
  ;; FIXME: make this a pure function instead of mutating `base-model`
  ;; 1. search for all addresses without longitude/latitude
  (let [results (search/run-select-query
                 base-model
                 "SELECT ?address ?country ?locality ?postcode ?street
                   WHERE {
                     ?address <http://schema.org/postalCode> ?postcode .
                     ?address <http://schema.org/streetAddress> ?street .
                     ?address <http://schema.org/addressLocality> ?locality .
                     OPTIONAL { ?address <http://schema.org/addressCountry> ?country . }

                     FILTER (
                      NOT EXISTS { ?address <http://www.w3.org/2003/01/geo/wgs84_pos#long> ?long }
                      ||
                      NOT EXISTS { ?address <http://www.w3.org/2003/01/geo/wgs84_pos#lat> ?lat }
                     )
                   }")
        ;; 2. for all results:
        ;;    fetch geo coordinates from OSM/Nominatim
        changed? (reduce (fn [changed? result]
                           (let [address (get result "address")
                                 postcode (str (get result "postcode"))
                                 street (str (get result "street"))
                                 locality (str (get result "locality"))
                                 country (str (get result "country"))

                                 osm-result (osm/search! (osm/address
                                                          osm/address-country country
                                                          osm/address-locality locality
                                                          osm/address-postcode postcode
                                                          osm/address-street street))]

                             (if (osm/search-success? osm-result)
                               ;; 3. write back geo triples
                               (let [lat (osm/search-success-latitude osm-result)
                                     long (osm/search-success-longitude osm-result)]

                                 ;; geo has longitude
                                 (.add base-model
                                       address
                                       (.createProperty base-model "http://www.w3.org/2003/01/geo/wgs84_pos#long")
                                       (.createTypedLiteral base-model long XSDDatatype/XSDdecimal))

                                 ;; geo has latitude
                                 (.add base-model
                                       address
                                       (.createProperty base-model "http://www.w3.org/2003/01/geo/wgs84_pos#lat")
                                       (.createTypedLiteral base-model lat XSDDatatype/XSDdecimal))

                                 true)

                               changed?)))
                         false
                         results)]
    [base-model changed?]))

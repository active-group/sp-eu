(ns wisen.backend.geocoding
  (:require
   [wisen.common.change-api :as change-api]))

(defn- map-comp [m1 m2]
  (reduce-kv (fn [mres k1 v1]
               (if-let [v2 (get m2 v1)]
                 (assoc mres k1 v2)
                 mres))
             {}
             m1))

(defn- changeset->places+addresses [changeset]
  (reduce (fn [[places addresses] change]
            (cond
              (change-api/add? change)
              (let [stmt (change-api/add-statement change)
                    subject (change-api/statement-subject stmt)
                    predicate (change-api/statement-predicate stmt)
                    object (change-api/statement-object stmt)]
                (cond
                  (and (= predicate "http://schema.org/address")
                       (change-api/uri? object))
                  ;; found a new place
                  [(assoc places subject object)
                   addresses]

                  (and (= predicate "http://schema.org/postalCode")
                       (change-api/literal-string? object))
                  ;; found a postal code
                  [places (assoc-in addresses [subject :postalCode] (change-api/literal-string-value object))]

                  (and (= predicate "http://schema.org/streetAddress")
                       (change-api/literal-string? object))
                  ;; found a street address
                  [places (assoc-in addresses [subject :streetAddress] (change-api/literal-string-value object))]

                  (and (= predicate "http://schema.org/addressLocality")
                       (change-api/literal-string? object))
                  ;; found a town
                  [places (assoc-in addresses [subject :addressLocality] (change-api/literal-string-value object))]

                  (and (= predicate "http://schema.org/addressCountry")
                       (change-api/literal-string? object))
                  ;; found a country
                  [places (assoc-in addresses [subject :addressCountry] (change-api/literal-string-value object))]

                  :else
                  [places addresses]))

              (change-api/delete? change)
              [places addresses]

              (change-api/with-blank-node? change)
              (assert false "Can only be called with skolemized changesets")))
          [{} {}]
          changeset))

(defn changeset->places [changeset]
  (let [[places addresses] (changeset->places+addresses changeset)]
    (map-comp places addresses)))

#_(statements->places [(change-api/make-statement "1" "http://schema.org/address" "2")
                     (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072"))
                     (change-api/make-statement "2" "http://schema.org/addressCountry" (change-api/make-literal-string "Germany"))])

(defn derived-geo-changeset [changeset place->lon-lat]
  (let [places (changeset->places changeset)]
    (mapcat (fn [[place-uri place]]
              (when-let [[lon lat] (place->lon-lat (:streetAddress place)
                                                   (:postalCode place)
                                                   (:addressLocality place)
                                                   (:addressCountry place))]
                (let [geo-uri (str place-uri "-geo")]
                  [(change-api/make-add
                    (change-api/make-statement place-uri "http://schema.org/geo" geo-uri))
                   (change-api/make-add
                    (change-api/make-statement geo-uri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" "http://schema.org/GeoCoordinates"))
                   (change-api/make-add
                    (change-api/make-statement geo-uri "http://schema.org/longitude" (change-api/make-literal-decimal (str lon))))
                   (change-api/make-add
                    (change-api/make-statement geo-uri "http://schema.org/latitude" (change-api/make-literal-decimal (str lat))))])))
            places)))

#_(derived-geo-changeset [(change-api/make-add (change-api/make-statement "1" "http://schema.org/address" "2"))
                          (change-api/make-add (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072")))
                          (change-api/make-add (change-api/make-statement "2" "http://schema.org/addressCountry" (change-api/make-literal-string "Germany")))]

                         (fn [street postcode locality country]
                           (condp = postcode
                             "72072" [48.12312 8])))

(defn add-geo-changeset [changeset place->lon-lat]
  (let [geo-changeset (derived-geo-changeset changeset place->lon-lat)]
    (concat changeset geo-changeset)))

#_(add-geo-changeset [(change-api/make-add (change-api/make-statement "1" "http://schema.org/address" "2"))
                    (change-api/make-add (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072")))
                    (change-api/make-add (change-api/make-statement "2" "http://schema.org/addressCountry" (change-api/make-literal-string "Germany")))]

                   (fn [street postcode locality country]
                     (condp = postcode
                       "72072" [48.12312 8])))

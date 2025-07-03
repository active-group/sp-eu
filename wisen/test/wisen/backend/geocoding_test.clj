(ns wisen.backend.geocoding-test
  (:require [clojure.test :refer [deftest testing is]]
            [wisen.backend.geocoding :as g]
            [wisen.common.change-api :as change-api]))

(deftest changeset->places-test
  (is (= {"1" {:postalCode "72072"}}
         (g/changeset->places [(change-api/make-add (change-api/make-statement "1" "http://schema.org/address" "2"))
                               (change-api/make-add (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072")))]))))

(deftest geocoding-test
  (testing "simple example"
    (is (= [(change-api/make-add
             (change-api/make-statement "http://example.org/1" "http://schema.org/geo" "http://example.org/1/geo"))
            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                                        "http://schema.org/GeoCoordinates"))
            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://schema.org/longitude"
                                        (change-api/make-literal-decimal "9.1")))

            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://schema.org/latitude"
                                        (change-api/make-literal-decimal "48.321")))]

           (g/derived-geo-changeset [(change-api/make-add (change-api/make-statement "http://example.org/1" "http://schema.org/address" "2"))
                                     (change-api/make-add (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072")))
                                     (change-api/make-add (change-api/make-statement "2" "http://schema.org/addressCountry" (change-api/make-literal-string "Germany")))]

                                    (fn [street postcode locality country]
                                      (condp = postcode
                                        "72072" [9.1 48.321]))))))

  (testing "place->lon-lat returns nil"
    (is (= [(change-api/make-add
             (change-api/make-statement "http://example.org/1" "http://schema.org/geo" "http://example.org/1/geo"))
            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                                        "http://schema.org/GeoCoordinates"))
            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://schema.org/longitude"
                                        (change-api/make-literal-decimal "9.1")))

            (change-api/make-add
             (change-api/make-statement "http://example.org/1/geo"
                                        "http://schema.org/latitude"
                                        (change-api/make-literal-decimal "48.321")))]

           (g/derived-geo-changeset [(change-api/make-add (change-api/make-statement "http://example.org/1" "http://schema.org/address" "2"))
                                     (change-api/make-add (change-api/make-statement "2" "http://schema.org/postalCode" (change-api/make-literal-string "72072")))
                                     (change-api/make-add (change-api/make-statement "2" "http://schema.org/addressCountry" (change-api/make-literal-string "Germany")))

                                     (change-api/make-add (change-api/make-statement "3" "http://schema.org/address" "4"))
                                     (change-api/make-add (change-api/make-statement "4" "http://schema.org/postalCode" (change-api/make-literal-string "12345")))]

                                    (fn [street postcode locality country]
                                      (condp = postcode
                                        "72072" [9.1 48.321]
                                        nil)))))))

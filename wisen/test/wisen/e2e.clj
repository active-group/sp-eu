(ns wisen.e2e
  (:require [etaoin.api :as e])
  (:gen-class))

(defn -main [& args]
  (let [driver (e/chrome
                {:path-driver "chromedriver"
                 :path-browser (System/getenv "CHROME_BIN")})
        url (or (first args) "http://localhost:4321")]
    (doto driver
      (e/driver-type)
      (e/go url)
      (e/wait-visible {:tag :input :type :text :id "username"})
      (e/fill {:tag :input :type :text :id "username"} "admin-user")
      (e/fill {:tag :input :type :password} "password")
      (e/click {:tag :button :type :submit :name "login"})
      (e/wait-visible {:xpath "//*[@id='headspinner'] | //a[text()='Search']"}))))

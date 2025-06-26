(ns wisen.e2e
  (:require [etaoin.api :as e])
  (:gen-class))

(defn -main [& _args]
  (let [driver (e/chrome
                {:path-driver "chromedriver"
                 :path-browser (System/getenv "CHROME_BIN")})]
    (doto driver
      (e/driver-type)
      (e/go "http://localhost:4321")
      (e/wait-visible {:tag :input :type :text :id "username"})
      (e/fill {:tag :input :type :text :id "username"} "admin-user")
      (e/fill {:tag :input :type :password} "password")
      (e/click {:tag :button :type :submit :name "login"})
      (e/wait-visible {:xpath "//a[text()='Search']"}))))

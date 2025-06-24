(ns wisen.backend.config
  (:require [active.clojure.config :as active.config]
            [active.clojure.openid.config :as openid.config]
            [clojure.edn :as edn]
            [active.clojure.logger.event :as event-logger]
            [clojure.string :as string]))

(def free-for-all?-setting
  (active.config/setting
   :free-for-all?
   "Is the authentication mechanism applied? Use this for development if needed."
   (active.config/boolean-range false)))

(def openid-profiles-section openid.config/openid-profiles-section)

(def openid-config-uri-setting openid.config/openid-provider-config-uri)

(def openid-first-profile-index 0)

(def openid-provider-section openid.config/openid-provider-section)

(defn active-group-gitlab-provider? [auth-config-section]
  (let [config-uri          (active.config/access auth-config-section openid-config-uri-setting
                                                  openid-profiles-section
                                                  openid-first-profile-index
                                                  openid-provider-section)
        active-group-gitlab "gitlab.active-group.de"]
    (and (string? config-uri)
         (string/includes? config-uri active-group-gitlab))))

(def auth-section
  (active.config/section
   :auth
   (active.config/schema "auth configuration schema"
                         free-for-all?-setting
                         openid-profiles-section)))

(def schema
  (active.config/schema "The SP-EU Web configuration schema"
                        auth-section))

(defn try-load-config
  "Trys to read the configuration from `path` using `profiles`."
  [path silent? & profiles]
  (try (when-not silent?
         (event-logger/log-event! :info (str "Reading configuration from path: " path))
         (when-not (empty? profiles)
           (event-logger/log-event! :info (str "Using profiles: " profiles))))
       (let [m (if (some? path)
                 (->> path
                      (slurp)
                      (edn/read-string))
                 {})]
         (active.config/make-configuration schema profiles m))
       (catch Exception e
         ;; TODO: take a closer look at what the errors in the config file are.
         (event-logger/log-event! :error (.getMessage e))
         (System/exit 2))))

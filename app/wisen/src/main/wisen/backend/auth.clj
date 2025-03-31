(ns wisen.backend.auth
  (:require [active.clojure.openid :as openid]
            [active.clojure.logger.event :as event-logger]
            [hiccup.page :as page]
            [ring.util.response :as response]
            [wisen.backend.config :as config]))

(defn redirect-login-handler
  [_req availables unavailables]
  (if-let [available-login (first availables)]
    (response/redirect (openid/available-login-uri available-login))
    (let [resp {:status  200
                :headers {"Content-Type" "text/html"}
                :body
                (page/html5
                 [:html {:style {:height "100%"}}
                  [:head
                   [:meta {:charset "utf-8"}]
                   [:title "SP-EU"]
                   [:meta {:name    "viewport"
                           :content "width=device-width, initial-scale=1"}]
                   [:link {:rel  "stylesheet"
                           :href "/css/style.css"}]]
                  [:body  {:style {:height "100%"}}
                   [:div {:style {:height "100%"}}
                    [:h3 "Login im Moment nicht möglich, da die Verbindung zum Identity-Provider nicht aufgebaut werden konnte."]
                    [:ul
                     (for [x (mapv openid/render-unavailable-login unavailables)]
                       [:li x])]]]])}]
      resp)))

(defn mk-sso-mw
  "Constructs a sso middleware, given a free-for-all? flag and an openid-config"
  [free-for-all? openid-config]
  (let [wrap-openid-authentication
        (openid/wrap-openid-authentication* openid-config
                                            :login-handler redirect-login-handler
                                            :logout-endpoint "/oidc-logout"
                                            :stubborn-idp-login-endpoint "/oidc-login-successful")]
    (fn sso-mw [handler]
      (if (not free-for-all?)
        (wrap-openid-authentication handler)
        handler))))

(defn authorised? [role req]
  (when-let [user-info (-> req
                           openid/maybe-user-info-from-request)]
    ;; 'groups' should contain the roles (mapping in active-openid/fetch-user-info)
    (when (empty? (openid/user-info-groups user-info))
      (event-logger/log-event! :info "Access denied: User authenticated but no roles/groups assigned."))
    (let [ok? (contains? (set (openid/user-info-groups user-info))
                         role)]
      (when-not ok?
        (event-logger/log-event! :info "Access denied: User lacks required roles."))
      ok?)))

(defn mk-authorised?-mw
  "Constructs an authorised? middleware given a free-for-all? flag."
  [free-for-all? openid-config]
  (fn authorised?-mw [handler]
    (if (not free-for-all?)
      (do (assert openid-config "Openid config required")
          (let [role (if (config/active-group-gitlab-provider? openid-config)
                       "ag"
                       "User")]
            (fn authorised?-handler [req]
              (if (authorised? role req)
                (handler req)
                (-> (response/response "Not authorised.")
                    (response/status 403))))))
      handler)))

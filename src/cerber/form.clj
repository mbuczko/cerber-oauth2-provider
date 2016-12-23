(ns cerber.form
  (:require [cerber.config :refer [app-config]]
            [cerber.oauth2
             [authenticator :refer [authentication-handler]]
             [context :as ctx]]
            [cerber.stores.user :as user]
            [failjure.core :as f]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :refer [header redirect response]]]
            [selmer.parser :refer [render-file]]))

(defn default-authenticator []
  (authentication-handler
   (get-in app-config [:cerber :authenticator] :default)))

(defn default-landing-url []
  (get-in app-config [:cerber :landing-url] "/"))

(defn default-authentication-endpoint []
  (get-in app-config [:cerber :enpoints :authentication] "/login"))

(defn default-approve-endpoint []
  (get-in app-config [:cerber :enpoints :client-approve] "/approve"))

(defn default-refuse-endpoint []
  (get-in app-config [:cerber :enpoints :client-refuse] "/refuse"))

(defn render-template [file kv]
  (-> (render-file file kv)
      (response)
      (header "Content-Type" "text/html")))

(defn render-login-form [req]
  (let [session (:session req)]
    (-> (render-template "forms/login.html" {:csrf (anti-forgery-field)
                                             :action (default-authentication-endpoint)
                                             :failed? (boolean (:failed? req))})

        ;; clear up auth info if already existed
        (assoc :session (dissoc session :login)))))

(defn render-approval-form [client req]
  (render-template "forms/authorize.html" {:csrf (anti-forgery-field)
                                           :client client
                                           :action-approve (str (default-approve-endpoint) "?" (:query-string req))
                                           :action-refuse  (str (default-refuse-endpoint) "?" (:query-string req))}))

(defn handle-login-submit [req]
  (let [result (ctx/user-password-valid? req (default-authenticator))]
    (if (f/failed? result)

      ;; login failed. re-render login page with failure flag set on.
      (render-login-form (assoc req :failed? true))

      ;; login succeeded. redirect either to session-stored or default landing url.
      (let [{:keys [login roles permissions]} (::ctx/user result)]
        (-> (get-in req [:session :landing-url] (default-landing-url))
            (redirect)
            (assoc :session {:login login
                             :roles roles
                             :permissions permissions}))))))

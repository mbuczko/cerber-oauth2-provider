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
   (:authenticator app-config :default)))

(defn authentication-endpoint []
  (get-in app-config [:endpoints :authentication]))

(defn approve-endpoint []
  (get-in app-config [:endpoints :client-approve]))

(defn refuse-endpoint []
  (get-in app-config [:endpoints :client-refuse]))

(defn render-template [file kv]
  (-> (render-file file kv)
      (response)
      (header "Content-Type" "text/html")))

(defn render-login-form [req]
  (let [session (:session req)]
    (-> (render-template "forms/login.html" {:csrf (anti-forgery-field)
                                             :action (authentication-endpoint)
                                             :failed? (boolean (:failed? req))})

        ;; clear up auth info if already existed
        (assoc :session (dissoc session :login)))))

(defn render-approval-form [client req]
  (render-template "forms/authorize.html" {:csrf (anti-forgery-field)
                                           :client client
                                           :action-approve (str (approve-endpoint) "?" (:query-string req))
                                           :action-refuse  (str (refuse-endpoint) "?" (:query-string req))}))

(defn handle-login-submit [req]
  (let [result (ctx/user-password-valid? req (default-authenticator))]
    (if (f/failed? result)

      ;; login failed. re-render login page with failure flag set on.
      (render-login-form (assoc req :failed? true))

      ;; login succeeded. redirect either to session-stored or default landing url.
      (let [{:keys [id login]} (::ctx/user result)]
        (-> (get-in req [:session :landing-url] (:landing-url app-config))
            (redirect)
            (assoc :session {:id id :login login}))))))

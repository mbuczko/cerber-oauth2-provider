(ns cerber.form
  (:require [cerber.oauth2
             [authenticator :refer [authentication-handler]]
             [context :as ctx]
             [settings :as settings]]
            [failjure.core :as f]
            [ring.util
             [anti-forgery :refer [anti-forgery-field]]
             [response :refer [header redirect response]]]
            [selmer.parser :refer [render-file]]))

(def default-authenticator (authentication-handler :default))

(defn render-template [file kv]
  (-> (render-file file kv)
      (response)
      (header "Content-Type" "text/html")))

(defn render-login-form [req]
  (let [session (:session req)]
    (-> (render-template "templates/cerber/login.html" {:csrf (anti-forgery-field)
                                                        :failed? (boolean (:failed? req))})

        ;; clear up auth info if already existed
        (assoc :session (dissoc session :id :login)))))

(defn render-approval-form [client scopes req]
  (render-template "templates/cerber/authorize.html" {:csrf (anti-forgery-field)
                                                      :query-params (:query-string req)
                                                      :client client
                                                      :scopes scopes}))

(defn handle-login-submit [req]
  (let [result (ctx/user-password-valid? req default-authenticator)]
    (if (f/failed? result)

      ;; login failed. re-render login page with failure flag set on.
      (render-login-form (assoc req :failed? true))

      ;; login succeeded. redirect either to session-stored or default landing url.
      (let [{:keys [id login]} (::ctx/user result)]
        (-> (get-in req [:session :landing-url] (settings/landing-url))
            (redirect)
            (assoc :session {:id id :login login}))))))

(ns cerber.form
  (:require [cerber.helpers :refer [ajax-request? cond-as->]]
            [cerber.oauth2
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
  (let [landing-url (get-in req [:session :landing-url])]
    (-> (render-template "templates/cerber/login.html" {:csrf (anti-forgery-field)
                                                        :failed? (boolean (:failed? req))})

        ;; convey landing-url if it was set on login
        (cond-> landing-url
          (assoc :session {:landing-url landing-url})))))

(defn render-approval-form [client scopes req]
  (render-template "templates/cerber/authorize.html" {:csrf (anti-forgery-field)
                                                      :query-params (:query-string req)
                                                      :client client
                                                      :scopes scopes}))

(defn handle-login-submit [req]
  (let [result (ctx/user-password-valid? req default-authenticator)
        ajax-request? (ajax-request? (:headers req))]

    (if (f/failed? result)

      ;; login failed. re-render login page with failure flag set on.
      (if ajax-request?
        {:status 401}
        (render-login-form (assoc req :failed? true)))

      ;; login succeeded. redirect either to session-stored or default landing url.
      (let [location (get-in req [:session :landing-url] (settings/landing-url))
            {:keys [id login]} (::ctx/user result)]
        (-> (redirect location)
            (assoc  :session {:id id :login login})
            (update :session dissoc :landing-url)

            ;; do not send HTTP 302 if login was requested via ajax-request.
            ;; client should do redirection by himself.

            (cond-as-> response
              ajax-request?
              (-> response
                  (update :headers merge {"content-type" "application/json"})
                  (assoc  :status 200
                          :body {:landing-url location}))))))))

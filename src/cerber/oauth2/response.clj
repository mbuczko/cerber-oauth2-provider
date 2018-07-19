(ns cerber.oauth2.response
  (:require [cerber
             [error :as error]
             [helpers :as helpers]
             [form :as form]]
            [cerber.oauth2
             [context :as ctx]
             [settings :as settings]]
            [cerber.stores
             [authcode :as authcode]
             [client :as client]
             [token :as token]]
            [failjure.core :as f]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.util
             [request :refer [request-url]]
             [response :as response]]
            [cerber.stores.user :as user]))


(defn redirect-to
  "Redirects browser to given location"

  [location]
  (response/redirect location))

(defn redirect-with-session [url session]
  (-> (redirect-to url)
      (assoc :session session)))

(defn redirect-with-code [{:keys [params ::ctx/user ::ctx/client ::ctx/scopes ::ctx/state ::ctx/redirect-uri]}]
  (f/attempt-all [access-scope (helpers/coll->str scopes)
                  authcode     (or (authcode/create-authcode client user access-scope redirect-uri) error/server-error)
                  redirect     (str redirect-uri
                                    "?code=" (:code authcode)
                                    (when state (str "&state=" state)))]
                 (redirect-to redirect)))

(defn redirect-with-token [{:keys [params ::ctx/user ::ctx/client ::ctx/scopes ::ctx/state ::ctx/redirect-uri]}]
  (f/attempt-all [access-scope (helpers/coll->str scopes)
                  access-token (token/generate-access-token client user access-scope)
                  redirect (str redirect-uri
                                "?access_token=" (:access_token access-token)
                                "&expires_in=" (:expires_in access-token)
                                (when access-scope (str "&scope=" access-scope))
                                (when state (str "&state=" state)))]
                 (redirect-to redirect)))

(defn access-token-response [{:keys [::ctx/client ::ctx/scopes ::ctx/user ::ctx/authcode]}]
  (when authcode
    (authcode/revoke-authcode authcode))

  ;; generate access & refresh token (if requested).
  ;;
  ;; note that scope won't exist in authorization_code scenario as it should be taken straight from authcode.
  ;; also, user won't exist for Client Credentials grant.

  (f/attempt-all [access-scope (and scopes (helpers/coll->str scopes))
                  access-token (token/generate-access-token client
                                                            (or user {:enabled? true})
                                                            (or access-scope (:scope authcode))
                                                            (boolean user))]
                 {:status 200
                  :body access-token}))

(defn refresh-token-response [req]
  (let [{:keys [login scope]} (::ctx/refresh-token req)]
    (f/attempt-all [access-token (token/generate-access-token (::ctx/client req)
                                                              (user/find-user login)
                                                              scope
                                                              true)]
                   {:status 200
                    :body access-token})))



(defn approval-form-response [req client scopes]
  ((wrap-anti-forgery
    (partial form/render-approval-form client scopes)) req))

(defn authentication-form-response [req]
  (redirect-with-session (settings/authentication-url)
                         {:landing-url (request-url req)}))

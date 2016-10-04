(ns cerber.oauth2.response
  (:require [cerber
             [error :as error]
             [form :as form]]
            [cerber.stores
             [authcode :as authcode]
             [token :as token]]
            [cerber.oauth2.context :as ctx]
            [failjure.core :as f]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.util
             [request :refer [request-url]]
             [response :as response]]))

(defn redirect-with-session [url session]
  (-> (response/redirect url)
      (assoc :session session)))

(defn redirect-with-code [{:keys [params ::ctx/user ::ctx/client ::ctx/scope ::ctx/state ::ctx/redirect-uri]}]
  (f/attempt-all [authcode (or (authcode/create-authcode client user scope redirect-uri) error/server-error)
                  redirect (str redirect-uri
                                "?code=" (:code authcode)
                                (when state (str "&state=" state)))]
                 (response/redirect redirect)))

(defn redirect-with-token [{:keys [params ::ctx/user ::ctx/client ::ctx/scope ::ctx/state ::ctx/redirect-uri]}]
  (f/attempt-all [access-token (token/generate-access-token client user scope)
                  redirect (str redirect-uri
                                "?access_token=" (:access_token access-token)
                                "&expires_in=" (:expires_in access-token)
                                (when scope (str "&scope=" scope))
                                (when state (str "&state=" state)))]
                 (response/redirect redirect)))

(defn access-token-response [{:keys [::ctx/client ::ctx/scope ::ctx/user ::ctx/authcode]}]
  (when authcode
    (authcode/revoke-authcode authcode))
  (f/attempt-all [access-token (token/generate-access-token client user (or scope (:scope authcode)))]
                 {:status 200
                  :body access-token}))

(defn refresh-token-response [req]
  (f/attempt-all [access-token (token/refresh-access-token (::ctx/refresh-token req))]
                 {:status 200
                  :body access-token}))

(defn approval-form-response [req client]
  ((wrap-anti-forgery
    (partial form/render-approval-form client)) req))

(defn authorization-form-response [req]
  (redirect-with-session "/login" {:landing-url (request-url req)}))

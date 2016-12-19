(ns cerber.oauth2.authorization
  (:require [cerber
             [config :refer [app-config]]
             [form  :as form]
             [error :as error]]
            [cerber.oauth2
             [context :as ctx]
             [response :as response]]
            [cerber.stores.user :as user]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(defmulti authorization-request-handler (comp :response_type :params))
(defmulti token-request-handler (comp :grant_type :params))

;; authorization request handler for Authorization Code grant type

(defmethod authorization-request-handler "code"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-valid?)
                            (ctx/redirect-allowed?)
                            (ctx/state-allowed?)
                            (ctx/scope-allowed?)
                            (ctx/user-authenticated?)
                            (ctx/request-auto-approved?))]
    (if (f/failed? result)
      result
      (response/redirect-with-code result))))

;; authorization request handler for Implict grant type

(defmethod authorization-request-handler "token"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-valid?)
                            (ctx/redirect-allowed?)
                            (ctx/grant-allowed? "token")
                            (ctx/state-allowed?)
                            (ctx/scope-allowed?)
                            (ctx/user-authenticated?))]
    (if (f/failed? result)
      result
      (response/redirect-with-token result))))

;; default response handler for unknown grant types

(defmethod authorization-request-handler :default
  [req]
  error/unsupported-response-type)

;; token request handler for Authorization Code grant type

(defmethod token-request-handler "authorization_code"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/grant-allowed? "authorization_code")
                            (ctx/authcode-valid?)
                            (ctx/redirect-valid?)
                            (ctx/user-valid?))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

;; token request handler for Resource Owner Password Credentials grant

(defmethod token-request-handler "password"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/grant-allowed? "password")
                            (ctx/scope-allowed?)
                            (ctx/user-password-valid? form/authenticator-fn))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

;; token request handler for Client Credentials grant

(defmethod token-request-handler "client_credentials"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/grant-allowed? "client_credentials")
                            (ctx/scope-allowed?))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

;; refresh-token request handler

(defmethod token-request-handler "refresh_token"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/refresh-token-valid?)
                            (ctx/scope-allowed?))]
    (if (f/failed? result)
      result
      (response/refresh-token-response result))))

;; default response handler for unknown token requests

(defmethod token-request-handler :default
  [req]
  error/unsupported-grant-type)

(defn authorize! [req]
  (let [response (authorization-request-handler req)]
    (condp = (:error response)
      "unapproved"   (response/approval-form-response req (:client response))
      "unauthorized" (response/authorization-form-response req)
      response)))

(defn approve! [req]
  (authorize! (ctx/approve-authorization req)))

(defn refuse! [req]
  error/access-denied)

(defn issue-token! [req]
  (token-request-handler req))

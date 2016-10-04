(ns cerber.oauth2.authorization
  (:require [cerber
             [config :refer [app-config]]
             [error :as error]]
            [cerber.oauth2
             [context :as ctx]
             [response :as response]]
            [cerber.stores.user :as user]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(defn default-auto-approver-fn
  "Auto-approver function. Unapproves all non-approved clients by default."
  [req]
  (when-let [client (::ctx/client req)]
    (:approved client)))

(defn default-authenticator-fn
  "Default user-authenticator function. Returns user if request is authorized or falsey otherwise."
  [req]
  (let [login (get-in req [:session :login])
        user (user/find-user login)]
    (and (:enabled user) user)))

(defstate arbiters
  :start (let [{:keys [auto-approver-fn authenticator-fn]} (:cerber app-config)]
           {:auto-approver (or (and auto-approver-fn (resolve auto-approver-fn)) default-auto-approver-fn)
            :authenticator (or (and authenticator-fn (resolve authenticator-fn)) default-authenticator-fn)}))

(defmulti authorization-request-handler (comp :response_type :params))
(defmulti token-request-handler (comp :grant_type :params))

(defmethod authorization-request-handler "code"
  [req]
  (let [result (f/attempt-> req
                            (ctx/state-allowed?)
                            (ctx/client-valid?)
                            (ctx/scope-allowed?)
                            (ctx/redirect-allowed?)
                            (ctx/user-authenticated? (:authenticator arbiters))
                            (ctx/request-auto-approved? (:auto-approver arbiters)))]
    (if (f/failed? result)
      result
      (response/redirect-with-code result))))

(defmethod authorization-request-handler "token"
  [req]
  (let [result (f/attempt-> req
                            (ctx/state-allowed?)
                            (ctx/client-valid?)
                            (ctx/scope-allowed?)
                            (ctx/redirect-allowed?)
                            (ctx/user-authenticated? (:authenticator arbiters)))]
    (if (f/failed? result)
      result
      (response/redirect-with-token result))))

(defmethod authorization-request-handler :default
  [req]
  error/unsupported-response-type)

(defmethod token-request-handler "authorization_code"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/authcode-valid?)
                            (ctx/redirect-valid?)
                            (ctx/user-valid?))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

(defmethod token-request-handler "password"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/scope-allowed?)
                            (ctx/user-password-valid?))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

(defmethod token-request-handler "client_credentials"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/scope-allowed?))]
    (if (f/failed? result)
      result
      (response/access-token-response result))))

(defmethod token-request-handler "refresh_token"
  [req]
  (let [result (f/attempt-> req
                            (ctx/client-authenticated?)
                            (ctx/refresh-token-valid?)
                            (ctx/scope-allowed?))]
    (if (f/failed? result)
      result
      (response/refresh-token-response result))))

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

(defn issue-token! [req]
  (token-request-handler req))

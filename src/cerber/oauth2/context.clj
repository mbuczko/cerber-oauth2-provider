(ns cerber.oauth2.context
  (:require [cerber.error :as error]
            [cerber.stores
             [authcode :as authcode]
             [client :as client]
             [token :as token]
             [user :as user]]
            [failjure.core :as f])
  (:import org.apache.commons.codec.binary.Base64))

(def refresh-token-pattern #"[A-Z0-9]{32}")

(defn basic-authentication-credentials
  "Decodes basic authentication credentials.
   If it exists it returns a vector of username and password. Returns nil otherwise."
  [req]
  (when-let [auth-string ((req :headers {}) "authorization")]
    (when-let [basic-token (last (re-find #"^Basic (.*)$" auth-string))]
      (when-let [credentials (String. (Base64/decodeBase64 basic-token))]
        (.split credentials ":")))))

(defn scope-allowed? [req]
  (let [scope (get-in req [:params :scope])] ;; can be optional
    (if (client/scope-valid? (::client req) scope)
      (assoc req ::scope scope)
      error/invalid-scope)))

(defn state-allowed? [req]
  (let [state (get-in req [:params :state])] ;; can be optional
    (if (or (nil? state)
            (re-matches #"\p{Alnum}+" state))
      (assoc req ::state state)
      error/invalid-state)))

(defn grant-allowed? [req mandatory-grant]
  (f/attempt-all [grant (or (get-in req [:params :grant_type]) error/invalid-request)
                  valid? (or (= grant mandatory-grant) error/invalid-grant)
                  allowed? (or (client/grant-allowed? (::client req) mandatory-grant) error/invalid-grant)]
                 (assoc req ::grant grant)))

(defn redirect-allowed? [req]
  (f/attempt-all [redirect-uri (or (get-in req [:params :redirect_uri]) error/invalid-request)
                  allowed? (or (client/redirect-uri-valid? (::client req) redirect-uri) error/invalid-redirect-uri)]
                 (assoc req ::redirect-uri (.replaceAll redirect-uri "/\\z" ""))))

(defn redirect-valid? [req]
  (f/attempt-all [redirect-uri (or (get-in req [:params :redirect_uri]) error/invalid-request)
                  valid? (or (= redirect-uri (:redirect-uri (::authcode req))) error/invalid-authcode)]
                 (assoc req ::redirect-uri (.replaceAll redirect-uri "/\\z" ""))))

(defn authcode-valid? [req]
  (f/attempt-all [code (or (get-in req [:params :code]) error/invalid-request)
                  authcode (or (authcode/find-authcode code) error/invalid-authcode)
                  valid? (or (= (:client-id authcode) (:id (::client req))) error/invalid-authcode)]
                 (assoc req ::authcode authcode)))

(defn refresh-token-valid? [req]
  (let [client-id (:id (::client req))]
    (f/attempt-all [refresh-token (or (get-in req [:params :refresh_token]) error/invalid-request)
                    match? (or (re-matches refresh-token-pattern refresh-token) error/invalid-token)
                    rtoken (or (token/find-refresh-token client-id refresh-token nil) error/invalid-token)
                    valid? (or (and (= client-id (:client-id rtoken))
                                    (= refresh-token (:secret rtoken))) error/invalid-token)]
                   (assoc req ::refresh-token rtoken))))

(defn client-valid? [req]
  (f/attempt-all [client-id (or (get-in req [:params :client_id]) error/invalid-request)
                  client (or (client/find-client client-id) error/invalid-client)]
                 (assoc req ::client client)))

(defn client-authenticated? [req]
  (f/attempt-all [auth (or (basic-authentication-credentials req) error/unauthorized)
                  client (or (client/find-client (first auth)) error/invalid-client)
                  valid? (or (= (second auth) (:secret client)) error/unauthorized)]
                 (assoc req ::client client)))

(defn user-authenticated? [req authenticator-fn]
  (if-let [user (authenticator-fn req)]
    (assoc req ::user user)
    error/unauthorized))

(defn user-password-valid? [req]
  (f/attempt-all [username (or (get-in req [:params :username]) error/invalid-request)
                  password (or (get-in req [:params :password]) error/invalid-request)
                  user   (or (user/find-user username) error/unauthorized)
                  valid? (or (user/valid-password? password (:password user)) error/unauthorized)]
                 (assoc req ::user user)))

(defn request-auto-approved? [req auto-approver-fn]
  (if (or (::approved? req) (auto-approver-fn req))
    req
    (assoc error/unapproved :client (::client req))))

(defn approve-authorization [req]
  (assoc req ::approved? true))

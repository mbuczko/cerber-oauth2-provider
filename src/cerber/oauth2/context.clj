(ns cerber.oauth2.context
  (:require [cerber.error :as error]
            [cerber.helpers :refer [expired?]]
            [cerber.oauth2.scopes :as scopes]
            [cerber.stores.authcode :as authcode]
            [cerber.stores.client :as client]
            [cerber.stores.token :as token]
            [cerber.stores.user :as user]
            [failjure.core :as f])
  (:import org.apache.commons.codec.binary.Base64))

(def state-pattern #"\p{Alnum}+")

(defn basic-authentication-credentials
  "Decodes basic authentication credentials.
   If it exists it returns a vector of username and password. Returns nil otherwise."
  [req]
  (when-let [auth-string ((req :headers {}) "authorization")]
    (when-let [basic-token (last (re-find #"^Basic (.*)$" auth-string))]
      (when-let [credentials (String. (Base64/decodeBase64 basic-token))]
        (.split credentials ":")))))

(defn state-allowed? [req]
  (let [state (get-in req [:params :state])]
    (f/attempt-all [valid? (or (nil? state)
                               (re-matches state-pattern state)
                               error/invalid-state)]
                   (assoc req ::state state))))

(defn scopes-allowed? [req allowed-scopes]
  (let [scope (get-in req [:params :scope])]
    (f/attempt-all [scopes (client/scope->arr scope)
                    valid? (or (client/scopes-valid? (::client req) scopes) error/invalid-scope)
                    allowed? (or (scopes/allowed-scopes? scopes allowed-scopes) error/invalid-scope)]
                   (assoc req ::scope (scopes/normalize-scopes scopes)))))

(defn grant-allowed? [req grant]
  (f/attempt-all [allowed? (or (client/grant-allowed? (::client req) grant) error/unsupported-grant-type)]
                 (assoc req ::grant grant)))

(defn redirect-allowed? [req]
  (f/attempt-all [redirect-uri (get-in req [:params :redirect_uri] error/invalid-request)
                  allowed? (or (client/redirect-uri-valid? (::client req) redirect-uri) error/invalid-redirect-uri)]
                 (assoc req ::redirect-uri (.replaceAll redirect-uri "/\\z" ""))))

(defn redirect-valid? [req]
  (f/attempt-all [redirect-uri (get-in req [:params :redirect_uri] error/invalid-request)
                  valid? (or (= redirect-uri (:redirect-uri (::authcode req))) error/invalid-request)]
                 (assoc req ::redirect-uri (.replaceAll redirect-uri "/\\z" ""))))

(defn authcode-valid? [req]
  (f/attempt-all [code (get-in req [:params :code] error/invalid-request)
                  authcode (or (authcode/find-authcode code) error/invalid-request)
                  valid?   (or (and (= (:client-id authcode) (:id (::client req)))
                                    (not (expired? authcode))) error/invalid-request)]
                 (assoc req ::authcode authcode)))

(defn refresh-token-valid? [req]
  (let [client-id (:id (::client req))]
    (f/attempt-all [refresh-token (get-in req [:params :refresh_token] error/invalid-request)
                    rtoken (or (token/find-refresh-token client-id refresh-token nil) error/invalid-token)
                    valid? (or (= client-id (:client-id rtoken)) error/invalid-token)]
                   (assoc req ::refresh-token rtoken))))

(defn bearer-valid? [req]
  (f/attempt-all [authorization (get-in req [:headers "authorization"] error/unauthorized)
                  bearer (or (second (.split authorization  " ")) error/unauthorized)
                  token  (or (token/find-access-token bearer) error/invalid-token)
                  valid? (or (not (expired? token)) error/invalid-token)]
                 (assoc req ::user (user/map->User {:id (:user-id token)
                                                    :login (:login token)
                                                    :roles nil
                                                    :permissions (:scope token)}))))

(defn user-valid? [req]
  (let [login (:login (::authcode req))]
    (f/attempt-all [user   (or (user/find-user login) error/invalid-request)
                    valid? (or (:enabled? user) error/unauthorized)]
                   (assoc req ::user user))))

(defn client-valid? [req]
  (f/attempt-all [client-id (get-in req [:params :client_id] error/invalid-request)
                  client (or (client/find-client client-id) error/invalid-request)]
                 (assoc req ::client client)))

(defn client-authenticated? [req]
  (f/attempt-all [auth   (or (basic-authentication-credentials req) error/unauthorized)
                  client (or (client/find-client (first auth)) error/invalid-request)
                  valid? (or (= (second auth) (:secret client)) error/invalid-request)]
                 (assoc req ::client client)))

(defn user-authenticated? [req]
  (if-let [user (user/find-user (-> req :session :login))]
    (assoc req ::user user)
    error/unauthorized))

(defn user-logged? [req]
  (when-let [session (:session req)]
    (when-let [login (:login session)]
      (assoc req ::user (user/map->User {:login login
                                         :roles (:roles session)
                                         :permissions (:permissions session)})))))

(defn user-password-valid? [req authenticator]
  (f/attempt-all [username (get-in req [:params :username] error/invalid-request)
                  password (get-in req [:params :password] error/invalid-request)
                  user     (or (.authenticate authenticator username password) error/unauthorized)]
                 (assoc req ::user user)))

(defn request-auto-approved? [req]
  (if (or (::approved? req)
          (:approved (::client req)))
    req
    (assoc error/unapproved :client (::client req))))

(defn approve-authorization [req]
  (assoc req ::approved? true))

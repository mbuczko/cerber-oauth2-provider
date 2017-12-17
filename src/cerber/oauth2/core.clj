(ns cerber.oauth2.core
  (:require [cerber.stores.client :as client]
            [cerber.stores.token :as token]
            [cerber.stores.user :as user]))

;; tokens

(defn find-tokens-by-client
  "Returns list of non-expirable refresh-tokens generated for given client."

  [client]
  (when-let [client-id (:id client)]
    (token/find-by-pattern [client-id "refresh" nil])))

(defn find-tokens-by-user
  "Returns list of non-expirable refresh-tokens generated for clients operating on behalf of given user."

  [user]
  (when-let [login (:login user)]
    (token/find-by-pattern [nil "refresh" nil login])))

(defn revoke-tokens
  "Revokes all access- and refresh-tokens bound with given client (and optional user)."

  ([client]
   (when-let [client-id (:id client)]
     (token/revoke-by-pattern [client-id nil])))
  ([client login]
   (when-let [client-id (:id client)]
     (token/revoke-by-pattern [client-id nil nil login]))))

;; clients

(defn find-client
  "Looks up for client with given identifier."

  [client-id]
  (client/find-client client-id))

(defn create-client
  "Creates new OAuth client.

  `info` is a non-validated info string (typically client's app name or URL to client's homepage)

  `redirects` is a validated vector of approved redirect-uris; redirect-uri provided with token request should match one of these entries

  `grants` is an optional vector of allowed grants: authorization_code, token, password or client_credentials; all grants allowed if set to nil

  `scopes` is an optional vector of OAuth scopes that client may request an access to

  `approved?` is an optional parameter deciding whether client should be auto-approved or not."

  [info redirects & [grants scopes approved?]]
  (client/create-client info redirects grants scopes approved?))

(defn modify-client
  [client])

(defn delete-client
  "Removes client from store along with all its access- and refresh-tokens."

  [client]
  (= 1 (client/revoke-client (:id client))))

;; users

(defn find-user
  "Looks up for a user with given login."

  [login]
  (user/find-user login))

(defn create-user
  "Creates new user with given login, descriptive name, user's email, password (stored as hash), roles and permissions.

  `enabled?` argument indicates whether user should be enabled by default (to be able to authenticate) or not."

  [login name email password roles permissions enabled?]
  (user/create-user (user/map->User {:login login
                                     :name  name
                                     :email email
                                     :enabled? enabled?})
                    password
                    roles
                    permissions))

(defn delete-user
  "Removes from store user with given login."

  [login]
  (= 1 (user/revoke-user login)))

(defn modify-user-status
  "Decides whether to enable or disable user with given login."

  [login enabled?]
  (when-let [user (find-user login)]
    (if enabled?
      (user/enable-user user)
      (user/disable-user user))
    (assoc user :enabled? enabled?)))

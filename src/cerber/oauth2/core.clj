(ns cerber.oauth2.core
  (:require [cerber.stores.client :as client]
            [cerber.stores.token :as token]
            [cerber.stores.user :as user]))

;; tokens

(defn find-tokens-by-client
  [client]
  (when-let [client-id (:id client)]
    (token/find-by-pattern [client-id "refresh" nil])))

(defn find-tokens-by-user
  [user]
  (when-let [login (:login user)]
    (token/find-by-pattern [nil "refresh" nil login])))

(defn revoke-tokens
  ([client]
   (when-let [client-id (:id client)]
     (token/revoke-by-pattern [client-id nil])))
  ([client login]
   (when-let [client-id (:id client)]
     (token/revoke-by-pattern [client-id nil nil login]))))

;; clients

(defn find-client
  [client-id]
  (client/find-client client-id))

(defn create-client
  [info redirects & [grants scopes approved?]]
  (client/create-client info redirects grants scopes approved?))

(defn modify-client
  [client])

(defn delete-client
  [client]
  (= 1 (client/revoke-client (:id client))))

;; users

(defn find-user
  [login]
  (user/find-user login))

(defn create-user
  [login name email password roles permissions enabled?]
  (user/create-user (user/map->User {:login login
                                     :name  name
                                     :email email
                                     :enabled? enabled?})
                    password
                    roles
                    permissions))

(defn delete-user
  [login]
  (= 1 (user/revoke-user login)))

(defn modify-user-status
  [login enabled?]
  (when-let [user (find-user login)]
    (if enabled?
      (user/enable-user user)
      (user/disable-user user))
    (assoc user :enabled? enabled?)))

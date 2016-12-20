(ns cerber.oauth2.core
  (:require [cerber.stores.client :as client]
            [cerber.stores.token :as token]))

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
  (client/create-client info redirects scopes grants approved?))

(defn modify-client
  [client])

(defn delete-client
  [client]
  (client/revoke-client (:id client)))

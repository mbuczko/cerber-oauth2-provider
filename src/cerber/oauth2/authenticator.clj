(ns cerber.oauth2.authenticator
  (:require [cerber.stores.user :as user]))

(defprotocol Authenticator
  (authenticate [this username password] "Returns authenticated user or nil if authentication failed"))

(defrecord FormAuthenticator []
  Authenticator
  (authenticate [this username password]
    (if-let [user (user/find-user username)]
      (and (user/valid-password? password (:password user))
           (:enabled? user)
           user))))
(defmulti authentication-handler identity)

(defmethod authentication-handler :default [_]
  (FormAuthenticator.))

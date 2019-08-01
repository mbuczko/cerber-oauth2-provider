(ns cerber.mappers
  (:require [cerber.helpers :as helpers]
            [taoensso.nippy :as nippy]))

(defn row->token
  [row]
  (when-let [{:keys [client_id user_id login scope secret created_at expires_at]} row]
    {:client-id client_id
     :user-id user_id
     :login login
     :scope scope
     :secret secret
     :created-at created_at
     :expires-at expires_at}))

(defn row->user
  [row]
  (when-let [{:keys [id login email name password roles created_at modified_at blocked_at]} row]
    (-> {:id id
         :login login
         :email email
         :name name
         :password password
         :created-at created_at
         :modified-at modified_at
         :roles (helpers/str->keywords roles)}
        (cond-> blocked_at
          (assoc :blocked-at blocked_at)))))

(defn row->session
  [row]
  (when-let [{:keys [sid content created_at expires_at]} row]
    {:sid sid
     :content (nippy/thaw content)
     :expires-at expires_at
     :created-at created_at}))

(defn row->client
  [row]
  (when-let [{:keys [id secret info approved scopes grants redirects created_at modified_at blocked_at]} row]
    (-> {:id id
         :secret secret
         :info info
         :approved? approved
         :scopes (helpers/str->coll scopes)
         :grants (helpers/str->coll grants)
         :redirects (helpers/str->coll redirects)
         :created-at created_at
         :modified-at modified_at
         :blocked-at blocked_at}
        (cond-> blocked_at
          (assoc :blocked-at blocked_at)))))

(defn row->authcode
  [row]
  (when-let [{:keys [client_id login code scope redirect_uri created_at expires_at]} row]
    {:client-id client_id
     :login login
     :code code
     :scope scope
     :redirect-uri redirect_uri
     :expires-at expires_at
     :created-at created_at}))

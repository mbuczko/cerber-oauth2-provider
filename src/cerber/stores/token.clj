(ns cerber.stores.token
  (:require [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [config :refer [app-config]]
             [store :refer :all]]
            [failjure.core :as f]
            [cerber.stores.user :as user]
            [cerber.error :as error])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :tokens :valid-for))

(defrecord Token [client-id user-id scope secret refreshing created-at expires-at])

(defn sql->map [{:keys [client_id user_id secret scope login refreshing created_at expires_at]}]
  {:client-id client_id
   :user-id user_id
   :secret secret
   :login login
   :scope scope
   :refreshing refreshing
   :expires-at expires_at
   :created-at created_at})

(defrecord SqlTokenStore []
  Store
  (fetch-one [this [client-id tag secret]]
    (when (= tag "access")
      (sql->map (first (db/find-access-token {:client-id client-id :secret secret})))))
  (fetch-all [this [client-id tag secret login]]
    (when (= tag "refresh")
      (map sql->map (if secret
                      (db/find-refresh-token-by-secret {:client-id client-id :secret secret})
                      (if client-id
                        (db/find-refresh-token-by-client {:client-id client-id :login login})
                        (db/find-refresh-token-by-login  {:login login}))))))
  (revoke-one! [this [client-id tag arg]]
    (db/delete-token {:client-id client-id :secret arg}))
  (store! [this k token]
    (when (= 1 (db/insert-token token)) token))
  (purge! [this]
    (db/clear-tokens)))

(defmulti create-token-store identity)

(defstate ^:dynamic *token-store*
  :start (create-token-store (-> app-config :cerber :tokens :store)))

(defmethod create-token-store :in-memory [_]
  (MemoryStore. "tokens" (atom {})))

(defmethod create-token-store :redis [_]
  (RedisStore. "tokens" (-> app-config :cerber :redis-spec)))

(defmethod create-token-store :sql [_]
  (SqlTokenStore.))

(defmacro with-token-store
  "Changes default binding to default token store."
  [store & body]
  `(binding [*token-store* ~store] ~@body))

(defn revoke-by-pattern
  [key]
  (revoke-all! *token-store* key)
  nil)

(defn revoke-token
  "Revokes existing token. Refresh tokens are removed along with all
  access tokens bound to the same client-user pair."
  [token]
  (let [{:keys [client-id login secret refreshing]} token]

    ;; when refresh token is removed, corresponding
    ;; access tokens should be removed as well

    (revoke-one! *token-store* [client-id "access" (or refreshing secret)])
    (when refreshing
      (revoke-one! *token-store* [client-id "refresh" secret login]))
    nil))

(defn create-token
  "Creates new token"
  [client user scope & [opts]]
  (let [{:keys [ttl refreshing]} opts
        expires (when (not refreshing)
                  (now-plus-seconds (or ttl (default-valid-for))))
        token (-> {:client-id (:id client)
                   :user-id (:id user)
                   :login (:login user)
                   :secret (generate-secret)
                   :scope scope
                   :refreshing refreshing
                   :expires-at expires
                   :created-at (java.util.Date.)
                   :tag (if refreshing "refresh" "access")})]

    (if-let [result (store! *token-store* (-> [:client-id :tag :secret]
                                              (cond-> refreshing (conj :login))) token)]
      (map->Token result)
      (error/internal-error "Cannot create token"))))

(defn find-by-key [key]
  (if-let [result (fetch-one *token-store* key)]
    (map->Token result)))

(defn find-by-pattern [key]
  (let [tokens (fetch-all *token-store* key)]
    (map (fn [t] (map->Token t)) tokens)))

(defn find-access-token
  [client-id secret]
  (find-by-key [client-id "access" secret]))

(defn find-refresh-token
  [client-id secret login]
  (first (find-by-pattern [client-id "refresh" secret login])))

(defn purge-tokens
  []
  "Removes token from store. Used for tests only."
  (purge! *token-store*))

(defn generate-access-token
  [client user scope]
  (let [access-token (create-token client user scope)
        {:keys [secret created-at expires-at login]} access-token]

    (if (f/failed? access-token)
      access-token
      (let [refresh-token (or (find-refresh-token (:id client) nil (:login user))
                              (create-token client user scope {:refreshing secret}))]

        (-> {:access_token secret
             :token_type "Bearer"
             :created_at created-at
             :expires_in (/ (- (.getTime expires-at)
                               (.getTime created-at)) 1000)}
            (cond-> scope
              (assoc :scope scope))
            (cond-> (not (f/failed? refresh-token))
              (assoc :refresh_token (:secret refresh-token))))))))

(defn refresh-access-token
  [refresh-token]
  (let [{:keys [client-id user-id login scope]} refresh-token]
    (revoke-token refresh-token)
    (generate-access-token {:id client-id}
                           {:id user-id :login login}
                           scope)))

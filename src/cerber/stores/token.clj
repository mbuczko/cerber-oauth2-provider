(ns cerber.stores.token
  (:require [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [config :refer [app-config]]
             [store :refer :all]]
            [failjure.core :as f]
            [cerber.stores.client :as client]
            [cerber.stores.user :as user]
            [cerber.error :as error]
            [clojure.tools.logging :as log])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :tokens :valid-for))

(defrecord Token [client-id user-id scope secret created-at expires-at])

(defrecord SqlTokenStore []
  Store
  (fetch [this [key tag]]
    (when-let [token (first (let [[client-id user-id] (.split key ":"), refresh? (= tag "refresh")]
                              (if user-id
                                (db/find-token-by-details {:client-id client-id :user-id user-id} :is-refresh refresh?)
                                (db/find-token-by-secret  {:secret key :is-refresh refresh?}))))]
      (let [{:keys [client_id user_id secret scope login is_refresh created_at expires_at]} token]
        {:client-id client_id
         :user-id user_id
         :secret secret
         :login login
         :scope scope
         :is-refresh is_refresh
         :expires-at expires_at
         :created-at created_at})))
  (revoke! [this [key tag]]
    (let [[client-id user-id] (.split key ":"), refresh? (= tag "refresh")]
      (if user-id
        (db/delete-token-by-details {:client-id client-id :user-id user-id} :is-refresh refresh?)
        (db/delete-token-by-secret  {:secret key :is-refresh refresh?}))))
  (store! [this k token]
    (when (or
           (= (count k) 3) ;; artificial index for nosql dbs
           (= 1 (db/insert-token token)))
      token))
  (modify! [this k token]
    token)
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

(defn revoke-token
  "Revokes previously generated token based on its secret."
  [token]
  (let [token-tag (when (:is-refresh token) "refresh")]
    (revoke! *token-store* [(:secret token) token-tag])
    (revoke! *token-store* [(str (:client-id token) ":" (:user-id token)) token-tag])
    nil))

(defn create-token
  "Creates new token"
  [client user scope & [opts]]
  (let [{:keys [ttl refresh?]} opts
        token-tag (when refresh? "refresh")
        expires   (when (not refresh?)
                    (now-plus-seconds (or ttl (default-valid-for))))
        token (-> {:client-id (:id client)
                   :user-id (:id user)
                   :login (:login user)
                   :secret (generate-secret)
                   :is-refresh refresh?
                   :scope scope
                   :tag token-tag
                   :expires-at expires
                   :created-at (java.util.Date.)})]

    (f/attempt-all [stored (or (store! *token-store* [:secret :tag] token)
                               (error/internal-error "Cannot create token"))
                    index  (or (store! *token-store* [:client-id :user-id :tag] token)
                               (error/internal-error "Cannot create token index record"))]
                   (map->Token stored))))

(defn find-by-key [key]
  (if-let [result (fetch *token-store* key)]
    (map->Token result)))

(defn find-access-token
  ([client-id user-id]
   (find-by-key [(str client-id ":" user-id)]))
  ([secret]
   (find-by-key [secret])))

(defn find-refresh-token
  ([client-id user-id]
   (find-by-key [(str client-id ":" user-id) "refresh"]))
  ([secret]
   (find-by-key [secret "refresh"])))

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
      (let [refresh-token (or (find-refresh-token (:id client) (:id user))
                              (create-token client user scope {:refresh? true}))]
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
    (generate-access-token (client/map->Client {:id client-id})
                           (user/map->User {:id user-id :login login})
                           scope)))

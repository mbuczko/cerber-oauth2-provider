(ns cerber.stores.token
  "Functions handling OAuth2 token storage."
  (:require [cerber.oauth2.settings :as settings]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [mappers :as mappers]
             [store :refer [Store ->MemoryStore ->RedisStore fetch-all fetch-one revoke-one! revoke-all! purge! store!]]]
            [failjure.core :as f]))

(def token-store (atom :not-initialized))

(defrecord Token [client-id user-id login scope secret created-at expires-at])

(defrecord SqlTokenStore [expired-tokens-cleaner]
  Store
  (fetch-one [this [ttype secret client-id login]]
    (some-> (db/find-tokens-by-secret {:secret secret :ttype ttype})
            mappers/row->token))
  (fetch-all [this [ttype secret client-id login]]
    (map mappers/row->token
         (db/find-tokens-by-client {:ttype ttype :client-id client-id})))
  (revoke-one! [this [ttype secret client-id login]]
    (db/delete-token-by-secret {:secret secret}))
  (revoke-all! [this [ttype secret client-id login]]
    (if login
      (db/delete-tokens-by-login  {:client-id client-id :login login :ttype ttype})
      (db/delete-tokens-by-client {:client-id client-id :ttype ttype})))
  (store! [this k token]
    (= 1 (db/insert-token token)))
  (purge! [this]
    (db/clear-tokens))
  (close! [this]
    (db/stop-periodic expired-tokens-cleaner)))

(defmulti create-token-store (fn [type opts] type))

(defmethod create-token-store :in-memory [_ _]
  (->MemoryStore "tokens" (atom {})))

(defmethod create-token-store :sql [_ db-conn]
  (db/bind-connection db-conn "tokens")
  (->SqlTokenStore (db/make-periodic 'cerber.db/clear-expired-tokens 60000)))

(defmethod create-token-store :redis [_ redis-spec]
  (->RedisStore "tokens" redis-spec))

(defn init-store
  "Initializes token store according to given type and configuration."

  [type config]
  (reset! token-store (create-token-store type config)))

(defn create-token
  "Creates and retuns new token."

  [ttype client user scope & [ttl access-secret]]
  (let [secret (helpers/generate-secret)
        token  (helpers/reset-ttl
                {:client-id (:id client)
                 :user-id (:id user)
                 :login (:login user)
                 :secret secret
                 :scope scope
                 :ttype (name ttype)
                 :created-at (helpers/now)
                 :access-secret (helpers/digest access-secret)}
                (and (= ttype :access) (or ttl (settings/token-valid-for))))
        keyvec  (if (= ttype :access)
                  [:ttype :secret]
                  [:ttype :secret :client-id :login])]

    (if (store! @token-store keyvec (update token :secret helpers/digest))
      (map->Token token)
      (error/internal-error "Cannot create token"))))

;; retrieval

(defn find-by-pattern
  "Finds token by vectorized pattern key. Each nil element of key will be
  replaced with wildcard specific for underlaying store implementation."

  [key]
  (map map->Token (fetch-all @token-store key)))

(defn find-by-key
  "Finds token by vectorized exact key. Each element of key is used to compose
  query depending on underlaying store implementation."

  [key]
  (when-let [result (fetch-one @token-store key)]
    (map->Token result)))

(defn find-access-token
  "Finds access token issued for given client with given secret code."

  [secret]
  (find-by-key ["access" (helpers/digest secret)]))

(defn find-refresh-token
  "Finds refresh token issued for given client with given secret code."

  [client-id secret]
  (find-by-key ["refresh" (helpers/digest secret)]))

(defn purge-tokens
  "Removes token from store."

  []
  (purge! @token-store))

;; revocation

(defn revoke-by-pattern
  [pattern]
  (revoke-all! @token-store pattern) nil)

(defn revoke-by-key
  [key]
  (revoke-one! @token-store key) nil)

(defn revoke-access-token
  [secret]
  (revoke-by-key ["access" (helpers/digest secret)]))

(defn revoke-client-tokens
  "Revokes access- and refresh-tokens of given client (and user optionally)."
  ([client]
   (revoke-client-tokens client nil))
  ([client user]
   (let [login (:login user)
         client-id (:id client)]

     ;; access-tokens are kept in no-sql stores under a key: access:<secret> for performance reasons,
     ;; but that makes them hard to revoke as there is no client/login information attached to the key.
     ;; on the other hand, refresh-tokens are stored under key: refresh:<secret>:<client-id>:<login>
     ;; which makes them pretty easy to filter. to overcome a problem with not-searchable access-tokens
     ;; their secrets are additionally stored along with refresh-tokens (as access-secret field).
     ;; this way, having a refresh token for given client/login found, we also immediately have an
     ;; access-token, which makes them both easily revokable.
     ;;
     ;; this is not a case for sql-stores which hold client/login information for both kind of tokens.
     ;; concluding, if there is no access-secret found in a refresh-token, it means it was fetched from
     ;; sql-store so both tokens may be revoked using `revoke-by-pattern`.

     (let [refresh-tokens (find-by-pattern ["refresh" nil client-id login])]
       (when (-> refresh-tokens first :access-secret)
         (doseq [token refresh-tokens]
           (revoke-by-key ["access" (:access-secret token)]))))

     ;; this won't work for no-sql stores
     (revoke-by-pattern ["access" nil client-id login])
     ;; this works for both kind of stores
     (revoke-by-pattern ["refresh" nil client-id login]))))

;; generation

(defn generate-access-token
  "Generates access-token for given client-login pair within provided scope.

  Additional options (`type` and `refresh?`) override default token type
  (Bearer) and generate refresh-token, which is not created by default.

  When called on client-user pair which already had tokens generated, effectively
  overrides both tokens revoking the old ones.

  To get tokens generated both client and user need to be enabled.
  Otherwise HTTP 400 (invalid request) is returned."

  [client user scope & [refresh? type]]

  (if (and (:enabled? client)
           (:enabled? user))
    (do

      ;; revoke all the tokens for given client/user that are still in use.
      (revoke-client-tokens client user)

      (let [result (create-token :access client user scope)
            {:keys [client-id secret created-at expires-at login]} result]

        (if (f/failed? result)
          result
          (let [refresh-token (when refresh? (create-token :refresh client user scope nil secret))]
            (-> {:access_token secret
                 :token_type (or type "Bearer")
                 :created_at created-at
                 :expires_in (quot (- (.getTime expires-at)
                                      (.getTime created-at)) 1000)}
                (cond-> scope
                  (assoc :scope scope))
                (cond-> (not (or (nil? refresh-token)
                                 (f/failed? refresh-token)))
                  (assoc :refresh_token (:secret refresh-token))))))))

        error/invalid-request))

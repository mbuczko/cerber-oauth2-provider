(ns cerber.stores.token
  (:require [clojure.string :refer [join split]]
            [cerber.stores.user :as user]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(def token-store (atom :not-initialized))

(def default-valid-for (atom 300))

(defrecord Token [client-id user-id login scope secret created-at expires-at])

(defrecord SqlTokenStore [normalizer valid-for]
  Store
  (fetch-one [this [client-id tag secret login]]
    (-> (db/call 'find-tokens-by-secret {:secret secret :tag tag})
        first
        normalizer))
  (fetch-all [this [client-id tag secret login]]
    (map normalizer (if secret
                      (db/call 'find-tokens-by-secret {:secret secret :tag tag})
                      (if client-id
                        (db/call 'find-tokens-by-login-and-client {:client-id client-id :login login :tag tag})
                        (db/call 'find-tokens-by-login {:login login :tag tag})))))
  (revoke-one! [this [client-id tag secret]]
    (db/call 'delete-token-by-secret {:secret secret}))
  (revoke-all! [this [client-id tag secret login]]
    (map ->Token (if login
                   (db/call 'delete-tokens-by-login  {:client-id client-id :login login :tag tag})
                   (db/call 'delete-tokens-by-client {:client-id client-id :tag tag}))))
  (store! [this k token]
    (when (= 1 (db/call 'insert-token token)) token))
  (purge! [this]
    (db/call 'clear-tokens)))

(defn normalize
  [token]
  (when-let [{:keys [client_id user_id login scope secret created_at expires_at]} token]
    (map->Token {:client-id client_id
                 :user-id user_id
                 :login login
                 :scope scope
                 :secret secret
                 :created-at created_at
                 :expires-at expires_at})))

(defmulti create-token-store (fn [type config] type))

(defmethod create-token-store :in-memory [_ _]
  (->MemoryStore "tokens" (atom {})))

(defmethod create-token-store :redis [_ redis-spec]
  (->RedisStore "tokens" redis-spec))

(defmethod create-token-store :sql [_ jdbc-spec]
  (db/init-pool jdbc-spec)
  (helpers/with-periodic-fn
    (->SqlTokenStore normalize) (db/interned-fn 'clear-expired-tokens) 60000))

(defn create-token
  "Creates new token."

  [tag client user scope & [ttl]]
  (let [secret (helpers/generate-secret)
        token  (helpers/reset-ttl
                {:client-id (:id client)
                 :user-id (:id user)
                 :login (:login user)
                 :secret (helpers/digest secret)
                 :scope scope
                 :tag (name tag)
                 :created-at (helpers/now)}
                (and (= tag :access) (or ttl @default-valid-for)))
        keyvec  (if (= tag :access)
                  [nil :tag :secret nil]
                  [:client-id :tag :secret :login])]

    (if-let [result (store! @token-store keyvec token)]
      (map->Token (assoc result :secret secret))
      (error/internal-error "Cannot create token"))))

;; revocation

(defn- revoke-by-pattern
  [pattern]
  (revoke-all! @token-store pattern) nil)

(defn- revoke-by-key
  [key]
  (revoke-one! @token-store key) nil)

(defn revoke-access-token
  [token]
  (when-let [secret (:secret token)]
    (revoke-by-key [nil "access" (helpers/digest (:secret token)) nil])))

(defn revoke-client-tokens
  ([client]
   (revoke-client-tokens client nil))
  ([client login]
   (revoke-by-pattern [(:id client) "access" nil login])
   (revoke-by-pattern [(:id client) "refresh" nil login])))

;; retrieval

(defn find-by-pattern
  "Finds token by vectorized pattern key.
  Each nil element of key will be replaced with wildcard specific for underlaying store implementation."

  [key]
  (when-let [tokens (fetch-all @token-store key)]
    (map (fn [t] (map->Token t)) tokens)))

(defn find-by-key
  "Finds token by vectorized exact key.
  Each element of key is used to compose query depending on underlaying store implementation."

  [key]
  (when-let [result (fetch-one @token-store key)]
    (map->Token result)))

(defn find-access-token
  "Finds access token issued for given client-user pair with particular auto-generated secret code."

  [secret]
  (find-by-key [nil "access" (helpers/digest secret) nil]))

(defn find-refresh-token
  "Finds refresh token issued for given client-user pair with particular auto-generated secret code."

  [client-id secret login]
  (first (find-by-pattern [client-id "refresh" (helpers/digest secret) login])))

(defn purge-tokens
  "Removes token from store."

  []
  (purge! @token-store))

;; generation

(defn generate-access-token
  "Generates access-token for given client-user pair within provided scope.
  Additional options (type, refresh?) may adjust token type (Bearer by default)
  and decide whether to generate refresh-token as well or not (no refresh-tokens by default).

  Asking again for refresh-token generation (through :refresh? true option) reuses prevously
  generated refresh-token for given client/user pair."

  [client user scope & [opts]]
  (let [access-token (and (nil? (revoke-by-pattern [(:id client) "access" nil (:login user)]))
                          (create-token :access client user scope))
        {:keys [client-id secret created-at expires-at login]} access-token
        {:keys [type refresh?] :or {type "Bearer"}} opts]

    (if (f/failed? access-token)
      access-token
      (let [refresh-token (and refresh?
                               (nil? (revoke-by-pattern [client-id "refresh" nil login]))
                               (create-token :refresh client user scope))]
        (-> {:access_token secret
             :token_type type
             :created_at created-at
             :expires_in (quot (- (.getTime expires-at)
                                  (.getTime created-at)) 1000)}
            (cond-> scope
              (assoc :scope scope))
            (cond-> (and refresh-token (not (f/failed? refresh-token)))
              (assoc :refresh_token (:secret refresh-token))))))))

(defn refresh-access-token
  "Refreshes access and refresh-tokens using provided refresh-token."

  [refresh-token]
  (let [{:keys [client-id user-id login scope]} refresh-token]
    (generate-access-token {:id client-id}
                           {:id user-id :login login}
                           scope
                           {:refresh? true})))

(defn init-store
  "Initializes token store according to given connection spec and
  optional token `valid-for` ttl."

  [type {:keys [jdbc-spec redis-spec valid-for]}]
  (when valid-for
    (reset! default-valid-for valid-for))

  (if-let [spec (or jdbc-spec redis-spec (= type :in-memory))]
    (reset! token-store (create-token-store type spec))
    (println (str "Connection spec missing for " type " type of store."))))

(ns cerber.stores.authcode
  "Functions handling OAuth2 authcode storage."

  (:require [clojure.string :refer [join split]]
            [cerber.stores.user :as user]
            [cerber.oauth2.settings :as settings]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(def authcode-store (atom :not-initialized))

(defrecord AuthCode [client-id login code scope redirect-uri expires-at created-at])

(defrecord SqlAuthCodeStore [normalizer cleaner]
  Store
  (fetch-one [this [code]]
    (-> (db/sql-call 'find-authcode {:code code})
        first
        normalizer))
  (revoke-one! [this [code]]
    (db/sql-call 'delete-authcode {:code code}))
  (store! [this k authcode]
    (when (= 1 (db/sql-call 'insert-authcode authcode)) authcode))
  (purge! [this]
    (db/sql-call 'clear-authcodes))
  (close! [this]
    (db/stop-periodic cleaner)))

(defn normalize
  [authcode]
  (when-let [{:keys [client_id login code scope redirect_uri created_at expires_at]} authcode]
    (map->AuthCode {:client-id client_id
                    :login login
                    :code code
                    :scope scope
                    :redirect-uri redirect_uri
                    :expires-at expires_at
                    :created-at created_at})))

(defmulti create-authcode-store (fn [type config] type))

(defmethod create-authcode-store :in-memory [_ _]
  (->MemoryStore "authcodes" (atom {})))

(defmethod create-authcode-store :redis [_ redis-spec]
  (->RedisStore "authcodes" redis-spec))

(defmethod create-authcode-store :sql [_ _]
  (->SqlAuthCodeStore normalize (db/make-periodic 'clear-expired-authcodes 8000)))

(defn init-store
  "Initializes authcode store according to given type and configuration."

  [type config]
  (reset! authcode-store (create-authcode-store type config)))

(defn revoke-authcode
  "Revokes previously generated authcode."

  [authcode]
  (revoke-one! @authcode-store [(:code authcode)]) nil)

(defn create-authcode
  "Creates and returns a new auth-code."

  [client user scope redirect-uri & [ttl]]
  (let [authcode (helpers/reset-ttl
                  {:client-id (:id client)
                   :login (:login user)
                   :scope scope
                   :code (helpers/generate-secret)
                   :redirect-uri redirect-uri
                   :created-at (helpers/now)}
                  (or ttl (settings/authcode-valid-for)))]

    (if (store! @authcode-store [:code] authcode)
      (map->AuthCode authcode)
      (error/internal-error "Cannot store authcode"))))

(defn find-authcode
  [code]
  (when-let [authcode (fetch-one @authcode-store [code])]
    (when-not (helpers/expired? authcode)
      (map->AuthCode authcode))))

(defn purge-authcodes
  "Removes auth code from store."

  []
  (purge! @authcode-store))

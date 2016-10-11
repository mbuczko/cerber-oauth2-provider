(ns cerber.stores.authcode
  (:require [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [helpers :as helpers]
             [config :refer [app-config]]
             [store :refer :all]]
            [failjure.core :as f]
            [cerber.stores.user :as user]
            [cerber.error :as error])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :authcodes :valid-for))

(defrecord AuthCode [client-id login code scope redirect-uri expires-at created-at])

(defn ->map [result]
  (when-let [{:keys [client_id login code scope redirect_uri created_at expires_at]} result]
    {:client-id client_id
     :login login
     :code code
     :scope scope
     :redirect-uri redirect_uri
     :expires-at expires_at
     :created-at created_at}))

(defrecord SqlAuthCodeStore []
  Store
  (fetch-one [this [code]]
    (->map (first (db/find-authcode {:code code}))))
  (revoke-one! [this [code]]
    (db/delete-authcode {:code code}))
  (store! [this k authcode]
    (when (= 1 (db/insert-authcode authcode)) authcode))
  (purge! [this]
    (db/clear-authcodes)))

(defmulti create-authcode-store identity)

(defstate ^:dynamic *authcode-store*
  :start (create-authcode-store (-> app-config :cerber :authcodes :store))
  :stop  (helpers/stop-collecting *authcode-store*))

(defmethod create-authcode-store :in-memory [_]
  (MemoryStore. "authcodes" (atom {})))

(defmethod create-authcode-store :redis [_]
  (RedisStore. "authcodes" (-> app-config :cerber :redis-spec)))

(defmethod create-authcode-store :sql [_]
  (helpers/with-garbage-collector
    (SqlAuthCodeStore.) db/clear-expired-authcodes 8000))

(defmacro with-authcode-store
  "Changes default binding to default authcode store."
  [store & body]
  `(binding [*authcode-store* ~store] ~@body))

(defn revoke-authcode
  "Revokes previously generated authcode."
  [authcode]
  (revoke-one! *authcode-store* [(:code authcode)]) nil)

(defn create-authcode
  "Creates new auth code"
  [client user scope redirect-uri & [ttl]]
  (let [authcode (helpers/reset-ttl
                  {:client-id (:id client)
                   :login (:login user)
                   :scope scope
                   :code (helpers/generate-secret)
                   :redirect-uri redirect-uri
                   :created-at (helpers/now)}
                  (or ttl (default-valid-for)))]

    (if (store! *authcode-store* [:code] authcode)
      (map->AuthCode authcode)
      (error/internal-error "Cannot store authcode"))))

(defn find-authcode [code]
  (if-let [authcode (fetch-one *authcode-store* [code])]
    (when-not (helpers/expired? authcode)
      (map->AuthCode authcode))))

(defn purge-authcodes
  []
  "Removes auth code from store. Used for tests only."
  (purge! *authcode-store*))

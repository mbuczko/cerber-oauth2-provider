(ns cerber.stores.authcode
  "Functions handling OAuth2 authcode storage."

  (:require [cerber.oauth2.settings :as settings]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]))

(def authcode-store (atom :not-initialized))

(defrecord AuthCode [client-id login code scope redirect-uri expires-at created-at])

(defrecord SqlAuthCodeStore [normalizer cleaner]
  Store
  (fetch-one [this [code]]
    (some-> (db/find-authcode {:code code})
            normalizer))
  (revoke-one! [this [code]]
    (db/delete-authcode {:code code}))
  (store! [this k authcode]
    (= 1 (db/insert-authcode authcode)))
  (purge! [this]
    (db/clear-authcodes))
  (close! [this]
    (db/stop-periodic cleaner)))

(defn normalize
  [authcode]
  (when-let [{:keys [client_id login code scope redirect_uri created_at expires_at]} authcode]
    {:client-id client_id
     :login login
     :code code
     :scope scope
     :redirect-uri redirect_uri
     :expires-at expires_at
     :created-at created_at}))

(defmulti create-authcode-store (fn [type config] type))

(defmethod create-authcode-store :in-memory [_ _]
  (->MemoryStore "authcodes" (atom {})))

(defmethod create-authcode-store :redis [_ redis-spec]
  (->RedisStore "authcodes" redis-spec))

(defmethod create-authcode-store :sql [_ db-conn]
  (when db-conn
    (db/bind-queries db-conn)
    (->SqlAuthCodeStore normalize (db/make-periodic 'cerber.db/clear-expired-authcodes 8000))))

(defn init-store
  "Initializes authcode store according to given type and configuration."

  [type config]
  (reset! authcode-store (create-authcode-store type config)))

(defn find-authcode
  [code]
  (when-let [authcode (fetch-one @authcode-store [code])]
    (when-not (helpers/expired? authcode)
      (map->AuthCode authcode))))

(defn revoke-authcode
  "Revokes previously generated authcode."

  [authcode]
  (revoke-one! @authcode-store [(:code authcode)]) nil)

(defn purge-authcodes
  "Removes auth code from store."

  []
  (purge! @authcode-store))

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

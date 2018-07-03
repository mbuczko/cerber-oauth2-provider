(ns cerber.stores.authcode
  (:require [clojure.string :refer [join split]]
            [cerber.stores.user :as user]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(def authcode-store (atom :not-initialized))

(def default-valid-for (atom 180))

(defrecord AuthCode [client-id login code scope redirect-uri expires-at created-at])

(defrecord SqlAuthCodeStore [normalizer]
  Store
  (fetch-one [this [code]]
    (-> (db/call 'find-authcode {:code code})
        first
        normalizer))
  (revoke-one! [this [code]]
    (db/call 'delete-authcode {:code code}))
  (store! [this k authcode]
    (when (= 1 (db/call 'insert-authcode authcode)) authcode))
  (purge! [this]
    (db/call 'clear-authcodes)))

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

(defmethod create-authcode-store :sql [_ jdbc-spec]
  (db/init-pool jdbc-spec)
  (helpers/with-periodic-fn
    (->SqlAuthCodeStore normalize) (db/interned-fn 'clear-expired-authcodes) 8000))

(defn revoke-authcode
  "Revokes previously generated authcode."

  [authcode]
  (revoke-one! @authcode-store [(:code authcode)]) nil)

(defn create-authcode
  "Creates new auth code."

  [client user scope redirect-uri & [ttl]]
  (let [authcode (helpers/reset-ttl
                  {:client-id (:id client)
                   :login (:login user)
                   :scope scope
                   :code (helpers/generate-secret)
                   :redirect-uri redirect-uri
                   :created-at (helpers/now)}
                  (or ttl @default-valid-for))]

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

(defn init-store
  "Initializes authcode store according to given connection spec and
  optional authcode `valid-for` ttl."

  [type {:keys [jdbc-spec redis-spec valid-for]}]
  (when valid-for
    (reset! default-valid-for valid-for))

  (if-let [spec (or jdbc-spec redis-spec (= type :in-memory))]
    (reset! authcode-store (create-authcode-store type spec))
    (println (str "Connection spec missing for " type " type of store."))))

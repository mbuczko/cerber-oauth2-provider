(ns cerber.stores.authcode
  (:require [clojure.string :refer [join split]]
            [cerber.stores.user :as user]
            [cerber
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]
            [failjure.core :as f]
            [mount.core :refer [defstate]]))

(defonce default-valid-for (atom 180))

(defrecord AuthCode [client-id login code scope redirect-uri expires-at created-at])

(defrecord SqlAuthCodeStore [normalizer {:keys [find-authcode
                                                delete-authcode
                                                insert-authcode
                                                clear-authcodes]}]
  Store
  (fetch-one [this [code]]
    (-> (find-authcode {:code code})
        first
        normalizer))
  (revoke-one! [this [code]]
    (delete-authcode {:code code}))
  (store! [this k authcode]
    (when (= 1 (insert-authcode authcode)) authcode))
  (purge! [this]
    (clear-authcodes)))

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

(defmethod create-authcode-store :in-memory [_ {:keys [valid-for]}]
  (reset! default-valid-for valid-for)
  (->MemoryStore "authcodes" (atom {})))

(defmethod create-authcode-store :redis [_ {:keys [redis-spec valid-for]}]
  (reset! default-valid-for valid-for)
  (->RedisStore "authcodes" (:redis-spec app-config)))

(defmethod create-authcode-store :sql [_ {:keys [jdbc-spec valid-for]}]
  (reset! default-valid-for valid-for)
  (let [fns (helpers/resolve-in-ns
             'cerber.db
             ['find-authcode 'delete-authcode 'insert-authcode 'clear-authcodes 'clear-expired-authcodes]
             :init-fn 'init-pool
             :init-args jdbc-spec)]

    (helpers/with-periodic-fn
      (->SqlAuthCodeStore normalize) (:clear-expired-authcodes fns) 8000)))

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
                  (or ttl @default-valid-for))]

    (if (store! *authcode-store* [:code] authcode)
      (map->AuthCode authcode)
      (error/internal-error "Cannot store authcode"))))

(defn find-authcode [code]
  (when-let [authcode (fetch-one *authcode-store* [code])]
    (when-not (helpers/expired? authcode)
      (map->AuthCode authcode))))

(defn purge-authcodes
  []
  "Removes auth code from store."
  (purge! *authcode-store*))

(defmacro with-authcode-store
  "Changes default binding to default authcode store."
  [store & body]
  `(binding [*authcode-store* ~store] ~@body))

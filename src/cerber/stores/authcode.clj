(ns cerber.stores.authcode
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [config :refer [app-config]]
             [store :refer :all]]
            [failjure.core :as f]
            [cerber.stores.user :as user]
            [cerber.error :as error])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :authcodes :valid-for))

(defrecord AuthCode [client-id user-id code scope redirect-uri expires-at created-at])

(defrecord SqlAuthCodeStore []
  Store
  (fetch [this [code]]
    (if-let [authcode (first (db/find-authcode {:code code}))]
      (let [{:keys [client_id user_id code scope redirect_uri created_at expires_at]} authcode]
        {:client-id client_id
         :user-id user_id
         :code code
         :scope scope
         :redirect-uri redirect_uri
         :expires-at expires_at
         :created-at created_at})))
  (revoke! [this [code]]
    (db/delete-authcode {:code code}))
  (store! [this k authcode]
    (when (= 1 (db/insert-authcode authcode)) authcode))
  (purge! [this]
    (db/clear-authcodes)))

(defmulti create-authcode-store identity)

(defstate ^:dynamic *authcode-store*
  :start (create-authcode-store (-> app-config :cerber :authcodes :store)))

(defmethod create-authcode-store :in-memory [_]
  (MemoryStore. "authcodes" (atom {})))

(defmethod create-authcode-store :redis [_]
  (RedisStore. "authcodes" (-> app-config :cerber :redis-spec)))

(defmethod create-authcode-store :sql [_]
  (SqlAuthCodeStore.))

(defmacro with-authcode-store
  "Changes default binding to default authcode store."
  [store & body]
  `(binding [*authcode-store* ~store] ~@body))

(defn revoke-authcode
  "Revokes previously generated authcode."
  [authcode]
  (revoke! *authcode-store* [(:code authcode)]) nil)

(defn create-authcode
  "Creates new auth code"
  [client user scope redirect-uri]
  (let [authcode {:client-id (:id client)
                  :user-id (:id user)
                  :login (:login user)
                  :scope scope
                  :code (generate-secret)
                  :redirect-uri redirect-uri
                  :expires-at (now-plus-seconds (default-valid-for))
                  :created-at (java.util.Date.)}]
    (if (store! *authcode-store* [:code] authcode)
      (map->AuthCode authcode)
      (error/internal-error "Cannot store authcode"))))

(defn find-authcode [code]
  (if-let [authcode (fetch *authcode-store* [code])]
    (if (expired? authcode)
      (revoke-authcode authcode)
      (map->AuthCode authcode))))

(defn purge-authcodes
  []
  "Removes auth code from store. Used for tests only."
  (purge! *authcode-store*))

(defn ->User
  "Returns User record filled in with all the user info that authcode holds."
  [authcode]
  (user/map->User {:id (:user-id authcode)
                   :login (:login authcode)}))

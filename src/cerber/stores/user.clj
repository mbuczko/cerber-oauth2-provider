(ns cerber.stores.user
  (:require [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [helpers :as helpers]
             [config :refer [app-config]]
             [store :refer :all]]
            [failjure.core :as f]))

(declare init-users)

(defrecord User [id login email name password enabled? created-at modified-at activated-at blocked-at])

(defrecord SqlUserStore [normalizer]
  Store
  (fetch-one [this [login]]
    (-> (db/find-user {:login login})
        first
        normalizer))
  (revoke-one! [this [login]]
    (db/delete-user {:login login}))
  (store! [this k user]
    (when (= 1 (db/insert-user user)) user))
  (modify! [this k user]
    (if (:enabled? user)
      (db/enable-user user)
      (db/disable-user user)))
  (purge! [this]
    (db/clear-users)))

(defn normalizer
  [user]
  (when-let [{:keys [id login email name password roles permissions created_at modified_at activated_at blocked_at enabled]} user]
    (map->User {:id id
                :login login
                :email email
                :name name
                :password password
                :enabled? enabled
                :created-at created_at
                :modified-at modified_at
                :activated-at activated_at
                :blocked-at blocked_at
                :roles (helpers/str->coll #{} roles)
                :permissions (helpers/str->coll #{} permissions)})))

(defmulti ^:no-doc create-user-store identity)

(defmacro with-user-store
  "Changes default binding to default users store."
  [store & body]
  `(binding [*user-store* ~store] ~@body))

(defstate ^:dynamic *user-store*
  :start (create-user-store (-> app-config :users :store)))

(defstate ^:no-doc defined-users
  :start (init-users (-> app-config :users :defined)))

(defmethod create-user-store :in-memory [_]
  (->MemoryStore "users" (atom {})))

(defmethod create-user-store :redis [_]
  (->RedisStore "users" (:redis-spec app-config)))

(defmethod create-user-store :sql [_]
  (->SqlUserStore normalizer))

(defn create-user
  "Creates new user"
  ([user password]
   (create-user user password nil nil))
  ([user password roles permissions]
   (let [enabled (:enabled? user true)
         merged  (merge-with
                  #(or %2 %1)
                  user
                  {:id (helpers/uuid)
                   :name nil
                   :email nil
                   :enabled? enabled
                   :password (and password (helpers/bcrypt-hash password))
                   :roles (helpers/coll->str roles)
                   :permissions (helpers/coll->str permissions)
                   :activated-at (when enabled (helpers/now))
                   :created-at (helpers/now)})]

     (when (store! *user-store* [:login] merged)
       (map->User merged)))))

(defn find-user
  "Returns users with given login, if found or nil otherwise."
  [login]
  (if-let [found (and login (fetch-one *user-store* [login]))]
    (map->User found)))

(defn revoke-user
  "Removes user from store"
  [user]
  (revoke-one! *user-store* [(:login user)]))

(defn enable-user
  "Enables user. Returns true if user has been enabled successfully or false otherwise."
  [user]
  (= 1 (modify! *user-store* [:login] (assoc user :enabled? true :activated-at (helpers/now)))))

(defn disable-user
  "Disables user. Returns true if user has been disabled successfully or false otherwise."
  [user]
  (= 1 (modify! *user-store* [:login] (assoc user :enabled? false :blocked-at (helpers/now)))))

(defn purge-users
  "Removes users from store. Used for tests only."
  []
  (purge! *user-store*))

(defn init-users
  "Initializes configured users."
  [users]
  (f/try*
   (reduce (fn [reduced {:keys [login email name permissions roles enabled? password]}]
             (conj reduced (create-user (map->User {:login login
                                                    :email email
                                                    :name name
                                                    :enabled? enabled?})
                                        password
                                        roles
                                        permissions)))
           {}
           users)))

(defn valid-password?
  "Verify that given password matches the hashed one."
  [password hashed]
  (and password hashed (helpers/bcrypt-check password hashed)))

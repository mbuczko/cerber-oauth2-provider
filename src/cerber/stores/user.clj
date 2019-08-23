(ns cerber.stores.user
  "Functions handling OAuth2 user storage."
  (:require [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [mappers :as mappers]
             [store :refer [Store ->MemoryStore ->RedisStore fetch-one revoke-one! purge! store! modify!]]]
            [clojure.string :as str]))

(def user-store (atom :not-initialized))

(defrecord User [id login email name password enabled? created-at modified-at blocked-at])

(defrecord SqlUserStore []
  Store
  (fetch-one [this [login]]
    (some-> (db/find-user {:login login})
            mappers/row->user))
  (revoke-one! [this [login]]
    (db/delete-user {:login login}))
  (store! [this k user]
    (= 1 (db/insert-user (update user :roles helpers/keywords->str))))
  (modify! [this k user]
    (= 1 (db/update-user (update user :roles helpers/keywords->str))))
  (purge! [this]
    (db/clear-users))
  (close! [this]
    ))

(defmulti create-user-store (fn [type opts] type))

(defmethod create-user-store :in-memory [_ _]
  (->MemoryStore "users" (atom {})))

(defmethod create-user-store :sql [_ db-conn]
  (db/bind-connection db-conn "users")
  (->SqlUserStore))

(defmethod create-user-store :redis [_ redis-spec]
  (->RedisStore "users" redis-spec))

(defn init-store
  "Initializes user store according to given type and configuration."

  [type config]
  (reset! user-store (create-user-store type config)))

(defn find-user
  "Returns users with given login if found or nil otherwise."

  [login]
  (when-let [user (and login (fetch-one @user-store [login]))]
    (let [enabled? (nil? (:blocked-at user))]
      (map->User (assoc user :enabled? enabled?)))))

(defn update-user
  "Updates user. Returns true if user has been updated or false otherwise."

  [user]
  (modify! @user-store [:login] (assoc user :modified-at (helpers/now))))

(defn revoke-user
  "Removes user from store"

  [user]
  (revoke-one! @user-store [(:login user)]))

(defn purge-users
  "Removes users from store."

  []
  (purge! @user-store))

(defn create-user
  "Creates and returns a new user, enabled by default."

  [login password details]
  (when (and login password)
    (let [user (-> details
                   (assoc :id (helpers/cerber-uuid)
                          :password (helpers/bcrypt-hash password)
                          :login login
                          :enabled? true
                          :created-at (helpers/now)))]

      (if (store! @user-store [:login] user)
        (map->User user)
        (error/internal-error "Cannot store user")))))

(defn valid-password?
  "Verifies that given password matches the hashed one."

  [password hashed]
  (and (not (str/blank? password))
       (not (str/blank? hashed))
       (helpers/bcrypt-check password hashed)))

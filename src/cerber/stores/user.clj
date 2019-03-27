(ns cerber.stores.user
  "Functions handling OAuth2 user storage."

  (:require [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [store :refer :all]]
            [clojure.string :as str]))

(def user-store (atom :not-initialized))

(defrecord User [id login email name password enabled? created-at modified-at activated-at blocked-at])

(defrecord SqlUserStore [normalizer]
  Store
  (fetch-one [this [login]]
    (some-> (db/sql-call 'find-user {:login login})
            normalizer))
  (revoke-one! [this [login]]
    (db/sql-call 'delete-user {:login login}))
  (store! [this k user]
    (= 1 (db/sql-call 'insert-user (update user :roles helpers/keywords->str))))
  (modify! [this k user]
    (if (:enabled? user)
      (db/sql-call 'enable-user user)
      (db/sql-call 'disable-user user)))
  (purge! [this]
    (db/sql-call 'clear-users))
  (close! [this]
    ))

(defn normalize
  [user]
  (when-let [{:keys [id login email name password roles created_at modified_at activated_at blocked_at enabled]} user]
    {:id id
     :login login
     :email email
     :name name
     :password password
     :enabled? enabled
     :created-at created_at
     :modified-at modified_at
     :activated-at activated_at
     :blocked-at blocked_at
     :roles (helpers/str->keywords roles)}))

(defmulti create-user-store (fn [type config] type))

(defmethod create-user-store :in-memory [_ _]
  (->MemoryStore "users" (atom {})))

(defmethod create-user-store :redis [_ redis-spec]
  (->RedisStore "users" redis-spec))

(defmethod create-user-store :sql [_ db-conn]
  (when db-conn
    (db/bind-queries db-conn)
    (->SqlUserStore normalize)))

(defn init-store
  "Initializes user store according to given type and configuration."

  [type config]
  (reset! user-store (create-user-store type config)))

(defn find-user
  "Returns users with given login if found or nil otherwise."

  [login]
  (when-let [user (and login (fetch-one @user-store [login]))]
    (map->User user)))

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

  [login password {:keys [name email roles enabled?] :as details}]
  (when (and login password)
    (let [user (-> details
                   (assoc :id (helpers/uuid)
                          :login login
                          :password (helpers/bcrypt-hash password)
                          :created-at (helpers/now)
                          :activated-at (when enabled? (helpers/now))))]

      (if (store! @user-store [:login] user)
        (map->User user)
        (error/internal-error "Cannot store user")))))

(defn enable-user
  "Enables user. Returns true if user has been enabled successfully or false otherwise."

  [user]
  (= 1 (modify! @user-store [:login] (assoc user :enabled? true :activated-at (helpers/now)))))

(defn disable-user
  "Disables user. Returns true if user has been disabled successfully or false otherwise."

  [user]
  (= 1 (modify! @user-store [:login] (assoc user :enabled? false :blocked-at (helpers/now)))))

(defn valid-password?
  "Verifies that given password matches the hashed one."

  [password hashed]
  (and (not (str/blank? password))
       (not (str/blank? hashed))
       (helpers/bcrypt-check password hashed)))

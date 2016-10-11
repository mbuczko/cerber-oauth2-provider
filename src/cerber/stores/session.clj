(ns cerber.stores.session
  (:require [cerber
             [config :refer [app-config]]
             [db :as db]
             [helpers :as helpers]
             [store :refer :all]]
            [mount.core :refer [defstate]]
            [taoensso.nippy :as nippy]
            [cerber.helpers :as helpers])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :sessions :valid-for))

(defrecord Session [sid content created-at expires-at])

(defn ->map [result]
  (when-let [{:keys [sid content created_at expires_at]} result]
    {:sid sid
     :content (nippy/thaw content)
     :expires-at expires_at
     :created-at created_at}))

(defrecord SqlSessionStore []
  Store
  (fetch-one [this [sid]]
    (->map (first (db/find-session {:sid sid}))))
  (revoke-one! [this [sid]]
    (db/delete-session {:sid sid}))
  (store! [this k session]
    (let [content (nippy/freeze (:content session))
          result  (db/insert-session (assoc session :content content))]
      (when (= 1 result) session)))
  (modify! [this k session]
    (let [result (db/update-session (assoc session :content (nippy/freeze (:content session))))]
      (when (= 1 result) session)))
  (touch! [this k session ttl]
    (let [extended (helpers/reset-ttl session ttl)
          result (db/update-session-expiration extended)]
      (when (= 1 result) extended)))
  (purge! [this]
    (db/clear-sessions)))

(defmulti create-session-store identity)

(defstate ^:dynamic *session-store*
  :start (create-session-store (-> app-config :cerber :sessions :store))
  :stop  (helpers/stop-collecting *session-store*))

(defmethod create-session-store :in-memory [_]
  (MemoryStore. "sessions" (atom {})))

(defmethod create-session-store :redis [_]
  (RedisStore. "sessions" (-> app-config :cerber :redis-spec)))

(defmethod create-session-store :sql [_]
  (helpers/with-garbage-collector
    (SqlSessionStore.) db/clear-expired-sessions 10000))

(defmacro with-session-store
  "Changes default binding to default session store."
  [store & body]
  `(binding [*session-store* ~store] ~@body))

(defn create-session
  "Creates new session"
  [content & [ttl]]
  (let [session (helpers/reset-ttl
                 {:sid (.toString (java.util.UUID/randomUUID))
                  :content content
                  :created-at (helpers/now)}
                 (or ttl (default-valid-for)))]

    (when (store! *session-store* [:sid] session)
      (map->Session session))))

(defn revoke-session
  "Revokes previously generated session."
  [session]
  (revoke-one! *session-store* [(:sid session)]) nil)

(defn update-session [session]
  (modify! *session-store* [:sid] (helpers/reset-ttl session (default-valid-for))))

(defn extend-session [session]
  (touch! *session-store* [:sid] session (default-valid-for)))

(defn find-session [sid]
  (when-let [found (fetch-one *session-store* [sid])]
    (when-not (helpers/expired? found)
      (map->Session found))))

(defn purge-sessions
  []
  "Removes sessions from store. Used for tests only."
  (purge! *session-store*))

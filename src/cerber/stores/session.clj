(ns cerber.stores.session
  "Functions handling OAuth2 session storage."

  (:require [cerber.helpers :as helpers]
            [cerber.oauth2.settings :as settings]
            [cerber
             [db :as db]
             [helpers :as helpers]
             [error :as error]
             [store :refer :all]]
            [mount.core :refer [defstate]]
            [taoensso.nippy :as nippy]))

(def session-store (atom :not-initialized))

(defrecord Session [sid content created-at expires-at])

(defrecord SqlSessionStore [normalizer cleaner]
  Store
  (fetch-one [this [sid]]
    (-> (db/sql-call 'find-session {:sid sid})
        first
        normalizer))
  (revoke-one! [this [sid]]
    (db/sql-call 'delete-session {:sid sid}))
  (store! [this k session]
    (let [content (nippy/freeze (:content session))]
      (= 1 (db/sql-call 'insert-session (assoc session :content content)))))
  (modify! [this k session]
    (let [result (db/sql-call 'update-session (update session :content nippy/freeze))]
      (when (= 1 result) session)))
  (touch! [this k session ttl]
    (let [extended (helpers/reset-ttl session ttl)
          result (db/sql-call 'update-session-expiration extended)]
      (when (= 1 result) extended)))
  (purge! [this]
    (db/sql-call 'clear-sessions))
  (close! [this]
    (db/stop-periodic cleaner)))

(defn normalize
  [session]
  (when-let [{:keys [sid content created_at expires_at]} session]
    {:sid sid
     :content (nippy/thaw content)
     :expires-at expires_at
     :created-at created_at}))

(defmulti create-session-store (fn [type config] type))

(defmethod create-session-store :in-memory [_ _]
  (->MemoryStore "sessions" (atom {})))

(defmethod create-session-store :redis [_ redis-spec]
  (->RedisStore "sessions" redis-spec))

(defmethod create-session-store :sql [_ db-conn]
  (when db-conn
    (db/bind-queries db-conn)
    (->SqlSessionStore normalize (db/make-periodic 'clear-expired-sessions 10000))))

(defn init-store
  "Initializes session store according to given type and configuration."

  [type config]
  (reset! session-store (create-session-store type config)))

(defn find-session [sid]
  (when-let [session (fetch-one @session-store [sid])]
    (when-not (helpers/expired? session)
      (map->Session session))))

(defn revoke-session
  "Revokes previously generated session."

  [session]
  (revoke-one! @session-store [(:sid session)]) nil)

(defn purge-sessions
  "Removes sessions from store."

  []
  (purge! @session-store))

(defn create-session
  "Creates and returns a new session."

  [content & [ttl]]
  (let [session (helpers/reset-ttl
                 {:sid (helpers/uuid)
                  :content content
                  :created-at (helpers/now)}
                 (or ttl (settings/session-valid-for)))]

    (if (store! @session-store [:sid] session)
      (map->Session session)
      (error/internal-error "Cannot store session"))))

(defn update-session [session]
  (modify! @session-store [:sid] (helpers/reset-ttl session (settings/session-valid-for))))

(defn extend-session [session]
  (touch! @session-store [:sid] session (settings/session-valid-for)))

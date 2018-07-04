(ns cerber.stores.session
  (:require [cerber.helpers :as helpers]
            [cerber
             [db :as db]
             [helpers :as helpers]
             [store :refer :all]]
            [mount.core :refer [defstate]]
            [taoensso.nippy :as nippy]))

(def session-store (atom :not-initialized))

(def default-valid-for (atom 3600))

(defrecord Session [sid content created-at expires-at])

(defrecord SqlSessionStore [normalizer cleaner]
  Store
  (fetch-one [this [sid]]
    (-> (db/call 'find-session {:sid sid})
        first
        normalizer))
  (revoke-one! [this [sid]]
    (db/call 'delete-session {:sid sid}))
  (store! [this k session]
    (let [content (nippy/freeze (:content session))
          result  (db/call 'insert-session (assoc session :content content))]
      (when (= 1 result) session)))
  (modify! [this k session]
    (let [result (db/call 'update-session (update session :content nippy/freeze))]
      (when (= 1 result) session)))
  (touch! [this k session ttl]
    (let [extended (helpers/reset-ttl session ttl)
          result (db/call 'update-session-expiration extended)]
      (when (= 1 result) extended)))
  (purge! [this]
    (db/call 'clear-sessions))
  (close! [this]
    (db/stop-periodic cleaner)))

(defn normalize
  [session]
  (when-let [{:keys [sid content created_at expires_at]} session]
    (map->Session {:sid sid
                   :content (nippy/thaw content)
                   :expires-at expires_at
                   :created-at created_at})))

(defmulti create-session-store (fn [type config] type))

(defmethod create-session-store :in-memory [_ _]
  (->MemoryStore "sessions" (atom {})))

(defmethod create-session-store :redis [_ redis-spec]
  (->RedisStore "sessions" redis-spec))

(defmethod create-session-store :sql [_ _]
  (->SqlSessionStore normalize (db/make-periodic 'clear-expired-sessions 10000)))

(defn create-session
  "Creates new session."

  [content & [ttl]]
  (let [session (helpers/reset-ttl
                 {:sid (helpers/uuid)
                  :content content
                  :created-at (helpers/now)}
                 (or ttl @default-valid-for))]

    (when (store! @session-store [:sid] session)
      (map->Session session))))

(defn revoke-session
  "Revokes previously generated session."

  [session]
  (revoke-one! @session-store [(:sid session)]) nil)

(defn update-session [session]
  (modify! @session-store [:sid] (helpers/reset-ttl session @default-valid-for)))

(defn extend-session [session]
  (touch! @session-store [:sid] session @default-valid-for))

(defn find-session [sid]
  (when-let [found (fetch-one @session-store [sid])]
    (when-not (helpers/expired? found)
      (map->Session found))))

(defn purge-sessions
  "Removes sessions from store."

  []
  (purge! @session-store))

(defn init-store
  "Initializes session store according to given connection spec and
  optional session `valid-for` ttl."

  [type config]
  (when-let [valid-for (:valid-for config)]
    (reset! default-valid-for valid-for))

  (reset! session-store (create-session-store type config)))

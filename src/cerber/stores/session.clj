(ns cerber.stores.session
  (:require [cerber.helpers :as helpers]
            [cerber
             [helpers :as helpers]
             [store :refer :all]]
            [mount.core :refer [defstate]]
            [taoensso.nippy :as nippy]))

(def default-valid-for (atom 3600))

(def ^:dynamic *session-store*)

(defrecord Session [sid content created-at expires-at])

(defrecord SqlSessionStore [normalizer {:keys [find-session
                                               delete-session
                                               insert-session
                                               update-session
                                               update-session-expiration
                                               clear-sessions]}]
  Store
  (fetch-one [this [sid]]
    (-> (find-session {:sid sid})
        first
        normalizer))
  (revoke-one! [this [sid]]
    (delete-session {:sid sid}))
  (store! [this k session]
    (let [content (nippy/freeze (:content session))
          result  (insert-session (assoc session :content content))]
      (when (= 1 result) session)))
  (modify! [this k session]
    (let [result (update-session (update session :content nippy/freeze))]
      (when (= 1 result) session)))
  (touch! [this k session ttl]
    (let [extended (helpers/reset-ttl session ttl)
          result (update-session-expiration extended)]
      (when (= 1 result) extended)))
  (purge! [this]
    (clear-sessions)))

(defn normalize
  [session]
  (when-let [{:keys [sid content created_at expires_at]} session]
    (map->Session {:sid sid
                   :content (nippy/thaw content)
                   :expires-at expires_at
                   :created-at created_at})))

(defmulti create-session-store (fn [type config] type))

(defmethod create-session-store :in-memory [_ {:keys [valid-for]}]
  (reset! default-valid-for valid-for)
  (->MemoryStore "sessions" (atom {})))

(defmethod create-session-store :redis [_ {:keys [redis-spec valid-for]}]
  (reset! default-valid-for valid-for)
  (->RedisStore "sessions" redis-spec))

(defmethod create-session-store :sql [_ {:keys [jdbc-spec valid-for]}]
  (reset! default-valid-for valid-for)
  (let [fns (helpers/resolve-in-ns
             'cerber.db
             ['find-session
              'delete-session
              'insert-session
              'update-session
              'update-session-expiration
              'clear-sessions
              'clear-expired-sessions]
             :init-fn 'init-pool
             :init-args jdbc-spec)]

    (helpers/with-periodic-fn
      (->SqlSessionStore normalize) (:clear-expired-sessions fns) 10000)))

(defn create-session
  "Creates new session"
  [content & [ttl]]
  (let [session (helpers/reset-ttl
                 {:sid (helpers/uuid)
                  :content content
                  :created-at (helpers/now)}
                 (or ttl @default-valid-for))]

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
  "Removes sessions from store."
  (purge! *session-store*))

(defmacro with-session-store
  "Changes default binding to default session store."
  [store & body]
  `(binding [*session-store* ~store] ~@body))

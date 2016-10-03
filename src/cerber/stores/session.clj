(ns cerber.stores.session
  (:require [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [cerber
             [db :as db]
             [config :refer [app-config]]
             [store :refer :all]]
            [cheshire.core :as json])
  (:import [cerber.store MemoryStore RedisStore]))

(defn default-valid-for []
  (-> app-config :cerber :sessions :valid-for))

(defn extend-by [session ttl]
  (assoc session :expires-at (now-plus-seconds (or ttl (default-valid-for)))))

(defrecord Session [sid content created-at expires-at])

(defrecord SqlSessionStore []
  Store
  (fetch-one [this [sid]]
    (if-let [session (first (db/find-session {:sid sid}))]
      (let [{:keys [sid content created_at expires_at]} session]
        {:sid sid
         :content (json/parse-string content true)
         :expires-at expires_at
         :created-at created_at})))
  (revoke-one! [this [sid]]
    (db/delete-session {:sid sid}))
  (store! [this k session]
    (let [content (json/generate-string (:content session))
          result  (db/insert-session (assoc session :content content))]
      (when (= 1 result) session)))
  (modify! [this k session]
    (let [result (db/update-session (assoc session :content (json/generate-string (:content session))))]
      (when (= 1 result) session)))
  (touch! [this k session]
    (let [result (db/update-session-expiration session)]
      (when (= 1 result) session)))
  (purge! [this]
    (db/clear-sessions)))

(defmulti create-session-store identity)

(defstate ^:dynamic *session-store*
  :start (create-session-store (-> app-config :cerber :sessions :store)))

(defmethod create-session-store :in-memory [_]
  (MemoryStore. "sessions" (atom {})))

(defmethod create-session-store :redis [_]
  (RedisStore. "sessions" (-> app-config :cerber :redis-spec)))

(defmethod create-session-store :sql [_]
  (SqlSessionStore.))

(defmacro with-session-store
  "Changes default binding to default session store."
  [store & body]
  `(binding [*session-store* ~store] ~@body))

(defn create-session
  "Creates new session"
  [content]
  (let [session {:sid (.toString (java.util.UUID/randomUUID))
                 :content content
                 :expires-at (now-plus-seconds (default-valid-for))
                 :created-at (java.util.Date.)}]
    (when (store! *session-store* [:sid] session)
      (map->Session session))))

(defn revoke-session
  "Revokes previously generated session."
  [session]
  (revoke-one! *session-store* [(:sid session)]) nil)

(defn update-session [session]
  (modify! *session-store* [:sid] (extend-by session nil)))

(defn extend-session [session ttl]
  (touch! *session-store* [:sid] (extend-by session ttl)))

(defn find-session [sid & [opt]]
  (when-let [found (fetch-one *session-store* [sid])]
    (if (expired? found)
      (revoke-session found)
      (map->Session
       (if-let [extend-by (:extend-by opt)]
         (extend-session found extend-by)
         found)))))


(defn purge-sessions
  []
  "Removes sessions from store. Used for tests only."
  (purge! *session-store*))

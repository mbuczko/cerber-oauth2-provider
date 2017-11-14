(ns cerber.helpers
  (:require [crypto.random :as random]
            [clojure.string :as str]
            [digest]))

(def scheduler ^java.util.concurrent.ScheduledExecutorService
  (java.util.concurrent.Executors/newScheduledThreadPool 1))

(defn now []
  (java.sql.Timestamp. (System/currentTimeMillis)))

(defn now-plus-seconds [seconds]
  (when seconds
    (java.sql.Timestamp. (+ (System/currentTimeMillis) (* 1000 seconds)))))

(defn init-periodic
  "Schedules a function f to be run periodically at given interval.
  Function gets {:date now} as an argument."

  [f interval]
  (let [runnable (proxy [Runnable] [] (run [] (f {:date (now)})))]
    (.scheduleAtFixedRate scheduler ^Runnable runnable 0 interval java.util.concurrent.TimeUnit/MILLISECONDS)))

(defn with-periodic-fn
  "Initializes periodically run function and associates it to given store."

  [store f interval]
  (assoc store :periodic (init-periodic f interval)))

(defn stop-periodic
  "Stops periodically run funciton attached to store."

  [store]
  (when-let [^java.util.concurrent.ScheduledFuture periodic (:periodic store)]
    (.cancel periodic false)))


(defn generate-secret
  "Generates a unique secret code."

  []
  (random/base32 20))

(defn expired?
  "Returns true if given item (more specifically its :expires-at value)
  is expired or falsey otherwise. Item with no expires-at is non-expirable."

  [item]
  (let [^java.sql.Timestamp expires-at (:expires-at item)]
    (and expires-at
         (.isBefore (.toLocalDateTime expires-at)
                    (java.time.LocalDateTime/now)))))

(defn reset-ttl
  "Extends time to live of given item by ttl seconds."

  [item ttl]
  (assoc item :expires-at (now-plus-seconds ttl)))

(defn str->vec
  "Decomposes string into vector of space separated substrings.
  Returns empty vector if string was either null or empty."

  [^String str]
  (or (and str
           (> (.length str) 0)
           (str/split str #" "))
      []))

(defn vec->str
  "Serializes vector of strings into single (space-separated) string."

  [vec]
  (str/join " " vec))

(defn expires->ttl
  "Returns number of seconds between now and expires-at."

  [^java.sql.Timestamp expires-at]
  (when expires-at
    (.between (java.time.temporal.ChronoUnit/SECONDS)
              (java.time.LocalDateTime/now)
              (.toLocalDateTime expires-at))))

(defn digest
  "Applies SHA-256 on given token"

  [secret]
  (digest/sha-256 secret))

(defn uuid
  "Generates uuid"

  []
  (.replaceAll (.toString (java.util.UUID/randomUUID)) "-" ""))

(defn assoc-if-exists
  "Assocs k with value v to map m only if there is already k associated."

  [m k v]
  (when (m k) (assoc m k v)))

(defn assoc-if-not-exists
  "Assocs k to store with value v only if no k was associated before."

  [m k v]
  (when-not (m k) (assoc m k v)))

(defn atomic-assoc-or-nil [a k v f]
  (get (swap! a f k v) k))

(ns cerber.helpers
  (:require [crypto.random :as random]
            [clojure.string :as str]
            [digest])
  (:import  [org.mindrot.jbcrypt BCrypt]))

(defn now []
  (java.sql.Timestamp. (System/currentTimeMillis)))

(defn now-plus-seconds [seconds]
  (when seconds
    (java.sql.Timestamp. (+ (System/currentTimeMillis) (* 1000 seconds)))))

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

(defn str->coll
  "Decomposes string into collection of space separated substrings.
  Returns given collection if string was either null or empty."

  [coll ^String str]
  (or (and str
           (> (.length str) 0)
           (into coll (str/split str #" ")))
      coll))

(defn coll->str
  "Serializes collection of strings into single (space-separated) string."

  [coll]
  (str/join " " coll))

(defn expires->ttl
  "Returns number of seconds between now and expires-at."

  [^java.sql.Timestamp expires-at]
  (when expires-at
    (.between (java.time.temporal.ChronoUnit/SECONDS)
              (java.time.LocalDateTime/now)
              (.toLocalDateTime expires-at))))

(defn digest
  "Applies SHA-256 on given secret."

  [secret]
  (digest/sha-256 secret))

(defn bcrypt-hash
  "Performs BCrypt hashing of password."

  [password]
  (BCrypt/hashpw password (BCrypt/gensalt)))

(defn bcrypt-check
  "Validates password against given hash."

  [password hashed]
  (BCrypt/checkpw password hashed))

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

(defn atomic-assoc-or-nil
  [a k v f]
  (get (swap! a f k v) k))

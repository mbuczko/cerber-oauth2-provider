(ns cerber.helpers
  (:require [crypto.random :as random]
            [clojure.string :as str]
            [failjure.core :as f]
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
  "Return a vector of `str` substrings split by space."

  [^String str]
  (into []
        (when (and str (> (.length str) 0))
          (str/split str #" "))))

(defn coll->str
  "Serializes collection of strings into single
  (space-separated) string."

  [coll]
  (str/join " " coll))

(defn str->keywords
  [str]
  (into #{} (map keyword) (str->coll str)))

(defn keywords->str
  [keywords]
  (coll->str (map #(subs (str %) 1) keywords)))

(defn str->int
  "Safely transforms stringified number into an Integer.
  Returns a Failure in case of any exception."

  [^String str]
  (f/try* (Integer/parseInt str)))

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

(defn cerber-uuid
  "Generates UUID tranformed to contain no separating dashes."

  []
  (.replaceAll (.toString (java.util.UUID/randomUUID)) "-" ""))

(defn cerber-uuid?
  "Returns true if `id` is a cerber-generated UUID, or false otherwise."

  [id]
  (boolean
   (and (string? id)
        (re-matches #"[a-f0-9]{32}" id))))

(defn ajax-request?
  "Returns true if X-Requested-With header was found with
  XMLHttpRequest value, returns false otherwise."

  [headers]
  (= (headers "x-requested-with") "XMLHttpRequest"))

(defn assoc-if-exists
  "Assocs k with value v to map m only if there is already k associated."

  [m k v]
  (when (m k) (assoc m k v)))

(defn assoc-if-not-exists
  "Assocs k with value v to map m only if no k was associated before."

  [m k v]
  (when-not (m k) (assoc m k v)))

(defn atomic-assoc-or-nil
  [a k v f]
  (get (swap! a f k v) k))

(defmacro cond-as->
  "A mixture of cond-> and as-> allowing more flexibility in the test and step forms.
  Stolen from https://juxt.pro/blog/posts/condas.html."

  [expr name & clauses]
  (assert (even? (count clauses)))
  (let [pstep (fn [[test step]] `(if ~test ~step ~name))]
    `(let [~name ~expr
           ~@(interleave (repeat name) (map pstep (partition 2 clauses)))]
       ~name)))

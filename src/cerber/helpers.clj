(ns cerber.helpers
  (:require [crypto.random :as random]
            [clojure.string :as str]))

(defn init-periodic
  "Periodically run garbage collecting function f.
  Function gets {:date now} as an argument."

  [f interval]
  (doto (Thread.
         #(try
            (while (not (.isInterrupted (Thread/currentThread)))
              (Thread/sleep interval)
              (f {:date (java.time.LocalDateTime/now)}))
            (catch InterruptedException _)))
    (.start)))

(defn stop-collecting [store]
  (when-let [periodic (:periodic store)]
    (.interrupt periodic)))

(defn with-garbage-collector [store f interval]
  (assoc store :periodic (init-periodic f interval)))

(defn generate-secret
  "Generates a unique secret code."
  []
  (random/base32 20))

(defn now-plus-seconds
  "Generates current datetime shifted forward by seconds."

  [seconds]
  (when seconds
    (.plusSeconds (java.time.LocalDateTime/now) seconds)))

(defn expired?
  "Returns true if given item (more specifically its :expires-at value)
  is expired or falsey otherwise. Item with no expires-at is non-expirable."

  [item]
  (let [expires-at (:expires-at item)]
    (and expires-at
         (> (compare (java.time.LocalDateTime/now) (.toLocalDateTime expires-at)) 0))))

(defn reset-ttl
  "Extends time to live of given item by ttl seconds."

  [item ttl]
  (assoc item :expires-at (now-plus-seconds ttl)))

(defn str->array
  "Decomposes space separated string into array.
  Returns empty array if string was either null or empty."

  [str]
  (or (and str
           (> (.length str) 0)
           (str/split str #" ")) []))

(defn array->str
  "Serializes array elements into string by joining them with space."

  [arr]
  (str/join " " arr))

(defn expires->ttl
  "Returns number of seconds between now and expires-at."

  [expires-at]
  (when expires-at
    (.between (java.time.temporal.ChronoUnit/SECONDS)
              expires-at
              (java.time.LocalDateTime/now))))

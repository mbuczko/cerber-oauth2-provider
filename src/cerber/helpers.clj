(ns cerber.helpers
  (:require [crypto.random :as random]
            [clojure.string :as str]))

(defn init-periodic
  "Runs periodically given function f"

  [f interval]
  (doto (Thread.
         #(try
            (while (not (.isInterrupted (Thread/currentThread)))
              (Thread/sleep interval)
              (f))
            (catch InterruptedException _)))
    (.start)))

(defn stop-periodic [periodic]
  (when periodic
    (.interrupt periodic)))

(defn generate-secret
  "Generates a unique secret code."
  []
  (random/base32 20))

(defn now-plus-seconds
  "Generates current datetime shifted forward by seconds."

  [seconds]
  (when seconds
    (java.util.Date/from (-> (java.time.LocalDateTime/now)
                             (.plusSeconds seconds)
                             (.atZone (java.time.ZoneId/systemDefault))
                             (.toInstant)))))

(defn expired?
  "Returns true if given item (more specifically its :expires-at value)
  is expired or falsey otherwise. Item with no expires-at is non-expirable."

  [item]
  (let [expires-at (:expires-at item)]
    (and expires-at
         (> (compare (java.util.Date.) expires-at) 0))))

(defn extend-ttl
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
  "Returns number of miliseconds between current and expires-at datetimes."

  [expires-at]
  (when expires-at
    (- (.getTime expires-at)
       (.getTime (java.util.Date.)))))

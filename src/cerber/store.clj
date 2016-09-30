(ns cerber.store
  (:require [taoensso.carmine :as car]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [crypto.random :as random]))

(def select-values
  (comp vals select-keys))

(defn ns-key [namespace composite]
  (str namespace "/" (str/join ":" (remove str/blank? composite))))

(defn generate-secret
  "Generates a unique secret"
  []
  (random/base32 20))

(defn now-plus-seconds [seconds]
  (when seconds
    (java.util.Date/from (-> (java.time.LocalDateTime/now)
                             (.plusSeconds seconds)
                             (.atZone (java.time.ZoneId/systemDefault))
                             (.toInstant)))))
(defn expires->ttl [expires-at]
  (when expires-at
    (- (.getTime expires-at)
       (.getTime (java.util.Date.)))))

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

(defn expired? [item]
  (if-let [expires-at (:expires-at item)]
    (> (compare (java.util.Date.) expires-at) 0)))

(defprotocol Store
  (fetch   [this k] "Finds the item based on a key")
  (revoke! [this k] "Invalidates or remove the item based on a key")
  (store!  [this k item] "Stores and returns new item with key taken from item map at k")
  (modify! [this k item] "Modifies item stored at key k")
  (touch!  [this k item] "Extends life time of given item")
  (purge!  [this] "Purges store"))

(defrecord MemoryStore [namespace store]
  Store
  (fetch [this k]
    (get @store (ns-key namespace k)))
  (revoke! [this k]
    (swap! store dissoc (ns-key namespace k)))
  (store! [this k item]
    (let [nskey (ns-key namespace (select-values item k))]
      (when-not (get @store nskey) ;; poor-man uniqueness check
        (get (swap! store assoc nskey item) nskey))))
  (modify! [this k item]
    (let [nskey (ns-key namespace (select-values item k))]
      (when (get @store nskey) ;; replace value already existing
        (get (swap! store assoc nskey item) nskey))))
  (touch! [this k item]
    (.modify! this k item))
  (purge! [this]
    (swap! store empty)))

(defrecord RedisStore [namespace server-conn]
  Store
  (fetch [this k]
    (car/wcar server-conn (car/get (ns-key namespace k))))
  (revoke! [this k]
    (car/wcar server-conn (car/del (ns-key namespace k))))
  (store! [this k item]
    (let [nskey (ns-key namespace (select-values item k))
          milis (expires->ttl (:expires-at item))
          result (car/wcar server-conn (if (and milis (> milis 0))
                                         (car/set nskey item "PX" milis)
                                         (car/set nskey item)))]
      (when (or (= result 1)
                (= result "OK")) item)))
  (modify! [this k item]
    (let [nskey (ns-key namespace (select-values item k))
          milis (expires->ttl (:expires-at item))
          result (car/wcar server-conn (if (and milis (> milis 0))
                                         (car/set nskey item "PX" milis "XX")
                                         (car/set nskey item "XX")))]
      (when (or (= result 1)
                (= result "OK")) item)))
  (touch! [this k item]
    (.modify! this k item))
  (purge! [this]
    (try
      (car/wcar server-conn (car/flushdb))
      (catch java.io.EOFException e
        (if-let [msg (.getMessage e)]
          (log/error msg))))))

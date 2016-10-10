(ns cerber.store
  (:require [taoensso.carmine :as car]
            [clojure.string :as str]
            [cerber.helpers :as helpers]))

(def select-values
  (comp vals select-keys))

(defn ns-key
  ([namespace composite nil-to]
   (ns-key namespace (mapv #(or %1 nil-to) composite)))
  ([namespace composite]
   (str namespace "/" (str/join ":" (remove str/blank? composite)))))

(defprotocol Store
  (fetch-one   [this k] "Finds single item based on exact key")
  (fetch-all   [this k] "Finds all items matching pattern key")
  (revoke-one! [this k] "Removes item based on exact key")
  (revoke-all! [this k] "Removes all items matching pattern key")
  (store!      [this k item] "Stores and returns new item with key taken from item map at k")
  (modify!     [this k item] "Modifies item stored at key k")
  (touch!      [this k item ttl] "Extends life time of given item by ttl seconds")
  (purge!      [this] "Purges store"))

(defrecord MemoryStore [namespace store]
  Store
  (fetch-one [this k]
    (get @store (ns-key namespace k)))
  (fetch-all [this k]
    (let [matcher (re-pattern (ns-key namespace k ".*"))]
      (vals (filter (fn [[s v]] (re-matches matcher s)) @store))))
  (revoke-one! [this k]
    (swap! store dissoc (ns-key namespace k)))
  (revoke-all! [this k]
    (let [matcher (re-pattern (ns-key namespace k ".*"))]
      (doseq [[s v] @store]
        (when (re-matches matcher s) (swap! store dissoc s)))))
  (store! [this k item]
    (let [nskey (ns-key namespace (select-values item k))]
      (when-not (get @store nskey) ;; poor-man uniqueness check
        (get (swap! store assoc nskey item) nskey))))
  (modify! [this k item]
    (let [nskey (ns-key namespace (select-values item k))]
      (when (get @store nskey) ;; replace value already existing
        (get (swap! store assoc nskey item) nskey))))
  (touch! [this k item ttl]
    (.modify! this k (assoc item :expires-at (helpers/now-plus-seconds ttl))))
  (purge! [this]
    (swap! store empty)))

(defn- scan-by-key [spec key]
  (car/reduce-scan
   (fn rf [acc in] (into acc in))
   []
   (fn scan-fn [cursor] (car/wcar spec (car/scan cursor :match key)))))

(defrecord RedisStore [namespace server-conn]
  Store
  (fetch-one [this k]
    ;; expired keys are auto-removed by Redis so it's way faster to clear up their
    ;; expiration time and pretend they're non-expirable than update expires-at at each touch
    (dissoc (car/wcar server-conn (car/get (ns-key namespace k))) :expires-at))
  (fetch-all [this k]
    (if-let [result (scan-by-key server-conn (ns-key namespace k "*"))]
      (filter (complement nil?)
              (car/wcar server-conn (apply (partial car/mget server-conn) result)))))
  (revoke-one! [this k]
    (car/wcar server-conn (car/del (ns-key namespace k))))
  (revoke-all! [this k]
    (if-let [result (scan-by-key server-conn (ns-key namespace k "*"))]
      (car/wcar server-conn (doseq [s result] (car/del s)))))
  (store! [this k item]
    (let [nskey (ns-key namespace (select-values item k))
          milis (helpers/expires->ttl (:expires-at item))
          result (car/wcar server-conn (if (and milis (> milis 0))
                                         (car/set nskey item "PX" milis)
                                         (car/set nskey item)))]
      (when (or (= result 1)
                (= result "OK")) item)))
  (modify! [this k item]
    (let [nskey (ns-key namespace (select-values item k))
          milis (helpers/expires->ttl (:expires-at item))
          result (car/wcar server-conn (if (and milis (> milis 0))
                                         (car/set nskey item "PX" milis "XX")
                                         (car/set nskey item "XX")))]
      (when (or (= result 1)
                (= result "OK")) item)))
  (touch! [this k item ttl]
    (let [nskey (ns-key namespace (select-values item k))]
      (car/wcar server-conn (car/pexpire nskey ttl))))
  (purge! [this]
    (try
      (car/wcar server-conn (car/flushdb))
      (catch java.io.EOFException e
        (if-let [msg (.getMessage e)]
          (println msg))))))

(ns cerber.common
  (:require [mount.core :refer [defstate] :as mount]
            [cerber.oauth2.authorization]
            [cerber.stores
             [user     :as u]
             [client   :as c]
             [session  :as s]
             [authcode :as a]
             ]
            [peridot.core :refer [request]]
            [clojure.data.codec.base64 :as b64])
  (:import redis.embedded.RedisServer))

(def redirect-uri "http://localhost")
(def scope "photo:read")
(def state "123ABC")

(defn redis-start []
  (when-let [redis (RedisServer. (Integer. 6380))]
    (.start redis)
    redis))

(defn redis-stop [instance]
  (and instance (.stop instance)))

;; in-memory redis instance

(defstate redis-instance
  :start (redis-start)
  :stop  (redis-stop redis-instance))

;; some additional midje checkers

(defn has-secret [field]
  (fn [actual]
    (not-empty (get actual field))))

(defn instance-of [clazz]
  (fn [actual]
    (instance? clazz actual)))

;; additional helpers

(defn extract-csrf [state]
  (second
   (re-find #"__anti\-forgery\-token.*value=\"([^\"]+)\"" (get-in state [:response :body]))))

(defn extract-access-code [state]
  (when-let [loc (get-in state [:response :headers "Location"])]
    (second
     (re-find #"code=([^\&]+)" loc))))

(defn base64-auth [client]
  (String. (b64/encode (.getBytes (str (:id client) ":" (:secret client)))) "UTF-8"))

(defn request-secured [state & opts]
  (let [token (extract-csrf state)]
    (apply request state (map #(if (map? %) (assoc % "__anti-forgery-token" token) %) opts))))

(defn create-test-user [login password]
  (u/purge-users)
  (u/create-user {:login login} password))

(defn create-test-client []
  (c/purge-clients)
  (c/create-client "test client" [redirect-uri] [scope] nil ["moderator"] false))

;; start testing system
(mount/start (mount/with-args {:env "test"}))

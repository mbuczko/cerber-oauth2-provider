(ns cerber.common
  (:require [cerber.server]
            [peridot.core :refer [request]]
            [clojure.data.codec.base64 :as b64]
            [mount.core :as mount])
  (:import redis.embedded.RedisServer))

;; in-memory redis instance
(defonce redis (RedisServer. (Integer. 6379)))

(defonce system (-> (mount/with-args {:env "test"})
                    (mount/except [#'cerber.server/http-server])
                    mount/start))

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

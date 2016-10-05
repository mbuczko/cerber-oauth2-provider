(ns cerber.oauth2.common
  (:require [cerber.server]
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

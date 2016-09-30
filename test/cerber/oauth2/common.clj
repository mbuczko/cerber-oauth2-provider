(ns cerber.oauth2.common
  (:require [mount.core :as mount]
            [cerber.stores.client :refer [create-client]]
            [cerber.server]
            [cerber.oauth2.provider]
            [clojure.tools.logging :as log])
  (:import redis.embedded.RedisServer))

;; in-memory redis instance
(defonce redis (RedisServer. (Integer. 6379)))

(defonce system (-> (mount/with-args {:env "test"})
                    (mount/except [#'cerber.server/http-server])
                    mount/start))

;; some additional checkers
(defn has-secret [field]
  (fn [actual]
    (not-empty (get actual field))))

(defn instance-of [clazz]
  (fn [actual]
    (instance? clazz actual)))

;; default users and clients
(def client-foo (create-client "http://foo.com" ["http://foo.com/callback"] nil nil nil))
(def client-bar (create-client "http://bar.com" ["http://bar.com/callback"] nil nil nil))

(def user-nioh {:login "nioh"})
(def user-niah {:login "niah"})

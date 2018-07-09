(ns cerber.test-utils
  (:require [cerber.db :as db]
            [cerber.store :refer :all]
            [cerber.stores
             [user :as u]
             [token :as t]
             [client :as c]
             [session :as s]
             [authcode :as a]]
            [conman.core :as conman]
            [clojure.data.codec.base64 :as b64]
            [peridot.core :refer [request header]])
  (:import redis.embedded.RedisServer))

(def redis-spec {:spec {:host "localhost"
                        :port 6380}})

(def jdbc-spec  {:init-size  1
                 :min-idle   1
                 :max-idle   4
                 :max-active 32
                 :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/schema.sql'"})

;; connection to testing H2 instance
(defonce db-conn
  (and (Class/forName "org.h2.Driver")
       (conman/connect! jdbc-spec)))

;; connection to testing redis instance
(defonce redis-instance
  (when-let [redis (RedisServer. (Integer. (-> redis-spec :spec :port)))]
    (.start redis)))

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

(defn random-string [n]
  (let [chars (map char (range 97 122)) ;; a-z
        login (take n (repeatedly #(rand-nth chars)))]
    (reduce str login)))

(defn request-secured [state & opts]
  (let [token (extract-csrf state)]
    (apply request state (map #(if (map? %) (assoc % "__anti-forgery-token" token) %) opts))))

(defn request-authorized [session url token]
  (slurp (-> session
             (header "Authorization" (str "Bearer " token))
             (request url)
             :response
             :body)))

(defn create-test-user
  ([password]
   (create-test-user nil password))
  ([user password]
   (u/create-user (or user {:login (random-string 12)}) password)))

(defn create-test-client
  "Creates test client - unapproved by default."

  ([scope redirect-uri]
   (create-test-client scope redirect-uri false))
  ([scope redirect-uri approved?]
   (c/create-client "test client" [redirect-uri] ["authorization_code" "token" "password" "client_credentials"] [scope] approved?)))

(defn disable-test-user [user]
  (u/disable-user user))

(defn enable-test-user [user]
  (u/enable-user user))

(defn init-stores
  "Initializes all the OAuth2 stores of given type and
  purges data kept by underlaying databases (H2 and redis)."

  [type store-params]
  (let [stores [(u/init-store type store-params)
                (c/init-store type store-params)
                (a/init-store type store-params)
                (s/init-store type store-params)
                (t/init-store type store-params)]]

    ;; clear all the database-related stuff as one connection
    ;; to redis & H2 is used across all the tests

    (u/purge-users)
    (c/purge-clients)
    (a/purge-authcodes)
    (s/purge-sessions)
    (t/purge-tokens)

    stores))

(defn close-stores
  "Closes all the stores provided in a collection."

  [stores]
  (doseq [store stores] (close! store)))

(defmacro with-stores
  [type & body]
  `(let [stores# (init-stores ~type (condp = ~type
                                      :sql db-conn
                                      :redis redis-spec
                                      nil))]
     ~@body
     (close-stores stores#)))

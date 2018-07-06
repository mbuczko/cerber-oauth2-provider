(ns cerber.test-utils
  (:require [cerber.stores
             [user :as u]
             [token :as t]
             [client :as c]
             [session :as s]
             [authcode :as a]]
            [peridot.core :refer [request header]]
            [clojure.data.codec.base64 :as b64]
            [cerber.db :as db]))

(def redis-spec {:spec {:host "localhost"
                        :port 6379}})

(def jdbc-spec  {:init-size  1
                 :min-idle   1
                 :max-idle   4
                 :max-active 32
                 :driver-class "org.h2.Driver"
                 :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/schema.sql'"})

;; connection to testing H2 instance
(defonce db-conn (db/init-pool jdbc-spec))

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
  [type store-params]
  (u/init-store type store-params)
  (c/init-store type store-params)
  (a/init-store type store-params)
  (s/init-store type store-params)
  (t/init-store type store-params)

  (when (= type :redis)
    (u/purge-users)
    (c/purge-clients)
    (a/purge-authcodes)
    (s/purge-sessions)
    (t/purge-tokens)))

(defmacro with-stores
  [type & body]
  `(let [db-conn# (init-stores ~type redis-spec)]
     ~@body))

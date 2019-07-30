(ns cerber.test-utils
  (:require [clojure.data.codec.base64 :as b64]
            [conman.core :as conman]
            [cerber.oauth2.core :as core]
            [cerber.store :refer [purge! close!]]
            [peridot.core :refer [request header]])
  (:import redis.embedded.RedisServer))

(def redis-spec {:spec {:host "localhost"
                        :port 6380}})

(def jdbc-spec  {:init-size  1
                 :min-idle   1
                 :max-idle   1
                 :max-active 1
                 :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/cerber_schema.sql'"})

(defonce db-conn  (and (Class/forName "org.h2.Driver")
                       (conman/connect! jdbc-spec)))

(defonce redis (try (doto (RedisServer. (Integer. (-> redis-spec :spec :port)))
                      (.start))
                    (catch Exception ex
                      (println "Redis already running"))))

;; some additional midje checkers

(defn has-secret [field]
  (fn [actual]
    (boolean (seq (get actual field)))))

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

(defn request-authorized [req url token]
  (-> req
      (header "Authorization" (str "Bearer " token))
      (request url)
      :response
      :body))

(defn create-test-user
  "Creates a test user - enabled by default."

  [& {:keys [login password enabled?] :or {enabled? true}}]
  (core/create-user (or login (random-string 12))
                    (or password (random-string 8))
                    :enabled? enabled?))

(defn create-test-client
  "Creates test client - enabled and unapproved by default."

  [redirect-uri & {:keys [scope approved?]}]
  (core/create-client ["authorization_code" "token" "password" "client_credentials"]
                      [redirect-uri]
                      :info "test client"
                      :scopes [scope]
                      :enabled? true
                      :approved? (boolean approved?)))

(defn disable-test-user [login]
  (core/disable-user login))

(defn disable-test-client [client-id]
  (core/disable-client client-id))

(defn enable-test-user [login]
  (core/enable-user login))

(defn init-stores
  "Initializes all the OAuth2 stores of given type and
  purges data kept by underlaying databases (H2 and redis)."

  [type]
  (let [store-params (case type
                       :sql db-conn
                       :redis redis-spec
                       nil)]
    [(core/create-user-store type store-params)
     (core/create-client-store type store-params)
     (core/create-authcode-store type store-params)
     (core/create-session-store type store-params)
     (core/create-token-store type store-params)]))

(defn purge-stores
  "Clears all the database-related stuff as one connection
  to redis & H2 is used across all the tests."

  [stores]
  (doseq [store stores]
    (when store (purge! store))))

(defn close-stores
  "Closes all the stores provided in a collection."

  [stores]
  (doseq [store stores] (close! store)))

(defmacro with-storage
  [type & body]
  `(let [stores# (init-stores ~type)]
     (purge-stores stores#)
     ~@body
     (close-stores stores#)))

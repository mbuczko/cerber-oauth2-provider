(ns cerber.common-test
  (:require [mount.core :as mount]
            [cerber.oauth2.authorization]
            [cerber.stores
             [user     :as u]
             [client   :as c]
             [session  :as s]
             [authcode :as a]]
            [peridot.core :refer [request]]
            [clojure.data.codec.base64 :as b64]))

;; fixtures

(def ^:const state "123ABC")

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

(defn create-test-client [scope redirect-uri]
  (c/purge-clients)
  (c/create-client "test client" [redirect-uri] ["authorization_code" "token" "password" "client_credentials"] [scope] false))

;; boot up testing system
(println "Starting testing system")
(mount/start)

(ns cerber.test-utils
  (:require [cerber.oauth2.standalone.system :as system]
            [cerber.stores
             [user   :as u]
             [client :as c]]
            [peridot.core :refer [request header]]
            [clojure.data.codec.base64 :as b64]))

(defonce test-system
  (system/go))

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

(defn create-test-client [scope redirect-uri & [id secret]]
  (c/create-client "test client" [redirect-uri] ["authorization_code" "token" "password" "client_credentials"] [scope] false id secret))

(ns cerber.oauth2.standalone.server
  (:require [cerber
             [store :refer :all]
             [handlers :as handlers]
             [helpers  :as helpers]]
            [cerber.oauth2
             [context :as ctx]
             [core :as core]]
            [cerber.stores
             [user :as user]
             [client :as client]]
            [cerber.oauth2.standalone.config :refer [app-config]]
            [compojure
             [core :refer [defroutes GET POST routes wrap-routes]]]
            [conman.core :as conman]
            [failjure.core :as f]
            [selmer.parser :as selmer]
            [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]))

(defonce db-conn
  (and (Class/forName "org.h2.Driver")
       (conman/connect! {:init-size  1
                         :min-idle   1
                         :max-idle   4
                         :max-active 32
                         :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/schema.sql'"
                         ;:driver-class "org.postgresql.Driver"
                         ;:jdbc-url "jdbc:postgresql://localhost:5432/template1?user=postgres"
                         })))

(defn user-info-handler
  [req]
  {:status 200
   :body (select-keys (::ctx/user req) [:login :name :email :roles :permissions])})

(defroutes oauth2-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defroutes restricted-routes
  (GET "/users/me" [] user-info-handler))

(def app-handler
  (wrap-defaults
   (routes oauth2-routes (-> restricted-routes
                             (wrap-routes handlers/wrap-authorized)))
   api-defaults))

(defn init-server
  "Initializes standalone HTTP server handling default OAuth2 endpoints."

  []
  (when-let [url (:landing-url app-config)]
    (core/set-landing-url! url))
  (when-let [http-config (:server app-config)]
    (web/run-server app-handler http-config)))

(defn init-users
  "Initializes pre-defined collection of test users."

  []
  (let [users (:users app-config)]
    (f/try*
     (doseq [{:keys [login email name permissions roles enabled? password]} users]
       (user/create-user {:login login
                          :email email
                          :name name
                          :enabled? enabled?}
                         password
                         roles
                         permissions)))))

(defn init-clients
  "Initializes pre-defined collection of test clients."

  []
  (let [clients (:clients app-config)]
    (f/try*
     (doseq [{:keys [id secret info redirects grants scopes approved?]} clients]
       (client/create-client info redirects grants scopes approved? id secret)))))


(defstate http-server
  :start (init-server)
  :stop  (when http-server (http-server)))

;; oauth2 stores

(defstate client-store
  :start (core/create-client-store :sql db-conn)
  :stop  (close! client-store))

(defstate user-store
  :start (core/create-user-store :sql db-conn)
  :stop  (close! user-store))

(defstate token-store
  :start (core/create-token-store :sql db-conn)
  :stop  (close! token-store))

(defstate authcode-store
  :start (core/create-authcode-store :sql db-conn)
  :stop  (close! authcode-store))

(defstate session-store
  :start (core/create-session-store :sql db-conn)
  :stop  (close! session-store))

;; oauth2 entities

(defstate users
  :start (init-users))

(defstate clients
  :start (init-clients))

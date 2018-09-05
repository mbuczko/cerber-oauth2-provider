(ns cerber.oauth2.standalone.server
  (:require [cerber.handlers :as handlers]
            [cerber.oauth2.context :as ctx]
            [cerber.oauth2.core :as core]
            [cerber.oauth2.standalone.config :refer [app-config]]
            [cerber.store :refer :all]
            [cerber.stores.client :as client]
            [compojure.core :refer [defroutes GET POST routes wrap-routes]]
            [conman.core :as conman]
            [failjure.core :as f]
            [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]))

(defn user-info-handler
  [req]
  {:status 200
   :body (select-keys (::ctx/user req) [:login :name :email :roles])})

(defroutes oauth2-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler)
  (GET  "/logout"    [] handlers/logout-handler))

(defroutes restricted-routes
  (GET "/users/me" [] user-info-handler))

(def app-handler
  (wrap-defaults
   (routes oauth2-routes
           (wrap-routes restricted-routes handlers/wrap-authorized))
   api-defaults))

(defn init-server
  "Initializes preconfigured users, clients and standalone
  HTTP server handling OAuth2 endpoints."

  []
  (core/init-users (:users app-config))
  (core/init-clients (:clients app-config))

  (when-let [url (:landing-url app-config)]
    (core/set-landing-url! url))
  (when-let [http-config (:server app-config)]
    (web/run-server app-handler http-config)))

(defstate db-conn
  :start (and (Class/forName "org.h2.Driver")
              (conman/connect! {:init-size  1
                                :min-idle   1
                                :max-idle   4
                                :max-active 32
                                :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/cerber_schema.sql'"
                                        ;:driver-class "org.postgresql.Driver"
                                        ;:jdbc-url "jdbc:postgresql://localhost:5432/template1?user=postgres"
                                }))
  :stop (conman/disconnect! db-conn))

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

(defstate http-server
  :start (init-server)
  :stop  (when http-server (http-server)))

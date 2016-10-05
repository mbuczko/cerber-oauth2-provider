(ns cerber.server
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST]]
            [mount.core :as mount :refer [defstate]]
            [cerber
             [config :refer [app-config]]
             [handlers :as handlers]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [selmer.parser :as selmer]
            [cerber.stores.user :as user]
            [cerber.stores.client :as client]))

(defroutes routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/authorize" [] handlers/authorization-approve-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defn init-users [users]
  (doseq [u users]
    (let [user (user/create-user {:login (:login u)} (:password u))]
      (log/info "User created" user))))

(defn init-clients [clients]
  (doseq [{:keys [homepage redirects scopes grants authorities approved]} clients]
    (let [client (client/create-client homepage
                                       redirects
                                       scopes
                                       grants
                                       authorities
                                       approved)]
      (log/info "Client created" client))))

(defn init-server
  "Initializes sample http server handling oauth2 endpoints."
  []
  (selmer/set-resource-path! (clojure.java.io/resource "templates"))

  ;; no caching for dev environment, please.
  (when (:is-dev? app-config) (selmer/cache-off!))

  (init-users (get-in app-config [:cerber :repository :users]))
  (init-clients (get-in app-config [:cerber :repository :clients]))

  (web/run-server
   (wrap-defaults routes api-defaults) (merge (:server app-config) (mount/args))))

(defstate http-server
  :start (init-server)
  :stop  (http-server))

(ns cerber.server
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes GET POST]]
            [mount.core :as mount :refer [defstate]]
            [cerber
             [config :refer [app-config]]
             [handlers :as handlers]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [selmer.parser :as selmer]))

(defroutes routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/authorize" [] handlers/authorization-approve-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defn init-server
  "Initializes sample http server handling oauth2 endpoints."
  []
  (selmer/set-resource-path! (clojure.java.io/resource "templates"))

  ;; no caching for dev environment, please.
  (when (:is-dev? app-config) (selmer/cache-off!))

  (log/info "setting up oauth2 server")
  (web/run-server
   (wrap-defaults routes api-defaults) (merge (:server app-config) (mount/args))))

(defstate http-server
  :start (init-server)
  :stop  (http-server))

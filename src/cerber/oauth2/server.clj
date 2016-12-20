(ns cerber.oauth2.server
  (:require [cerber
             [config :refer [app-config]]
             [handlers :as handlers]]
            [cerber.oauth2.context :as ctx]
            [compojure.core :refer [defroutes GET POST routes wrap-routes]]
            [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [selmer.parser :as selmer]))

(defn user-info-handler [req]
  {:status 200
   :body (::ctx/user req)})

(defroutes oauth2-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defroutes restricted-routes
  (GET "/user/info" [] user-info-handler))

(def app-handler
  (wrap-defaults
   (routes oauth2-routes (-> restricted-routes
                             (wrap-routes handlers/wrap-authorized)))
   api-defaults))

(defn init-server
  "Initializes sample http server handling oauth2 endpoints."
  []
  (selmer/set-resource-path! (clojure.java.io/resource "templates"))

  ;; no caching for dev environment, please.
  (when (:is-dev? app-config) (selmer/cache-off!))

  (web/run-server app-handler (merge (:server app-config) (mount/args))))

(defstate http-server
  :start (init-server)
  :stop  (http-server))

(ns cerber.oauth2.standalone.server
  (:require [cerber
             [config :refer [app-config]]
             [handlers :as handlers]]
            [cerber.oauth2.context :as ctx]
            [compojure
             [core :refer [defroutes GET POST routes wrap-routes]]]
            [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [selmer.parser :as selmer]))

(defn user-info-handler [req]
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

(def ^:no-doc app-handler
  (wrap-defaults
   (routes oauth2-routes (-> restricted-routes
                             (wrap-routes handlers/wrap-authorized)))
   api-defaults))

(defn init-server
  "Initializes standalone HTTP server handling default OAuth2 endpoints."

  []
  (when-let [http-config (:server app-config)]
    (web/run-server app-handler http-config)))

(defstate ^:no-doc http-server
  :start (init-server)
  :stop  (when http-server (http-server)))

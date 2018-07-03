(ns cerber.oauth2.standalone.server
  (:require [cerber
             [config :refer [app-config]]
             [handlers :as handlers]
             [helpers  :as helpers]]
            [cerber.oauth2.context :as ctx]
            [cerber.oauth2.core :as core]
            [cerber.stores.user :as user]
            [compojure
             [core :refer [defroutes GET POST routes wrap-routes]]]
            [mount.core :as mount :refer [defstate]]
            [org.httpkit.server :as web]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [selmer.parser :as selmer]
            [cerber.stores.client :as client]))

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

(defn init-users
  "Initializes pre-defined collection of users."

  []
  (let [users (-> app-config :users :init)]
    (doseq [{:keys [login email name permissions roles enabled? password]} users]
      (user/create-user ({:login login
                          :email email
                          :name name
                          :enabled? enabled?})
                        password
                        roles
                        permissions))))

(defn init-clients
  "Initializes pre-defined collection of clients."

  []
  (let [clients (-> app-config :clients :init)]
    (doseq [{:keys [id secret info redirects grants scopes approved?]} clients]
      (client/create-client info redirects grants scopes approved? id secret))))


(defstate ^:no-doc http-server
  :start (init-server)
  :stop  (when http-server (http-server)))

;; stores

(defstate ^:no-doc client-store
  :start (core/create-client-store :sql app-config))

(defstate ^:no-doc user-store
  :start (core/create-user-store :sql app-config))

(defstate ^:no-doc token-store
  :start (core/create-token-store :sql app-config)
  :stop  (helpers/stop-periodic token-store))

(defstate ^:no-doc authcode-store
  :start (core/create-authcode-store app-config)
  :stop  (helpers/stop-periodic authcode-store))

(defstate ^:no-doc session-store
  :start (core/create-session-store app-config)
  :stop  (helpers/stop-periodic session-store))

;; oauth2 entities

(defstate ^:no-doc users
  :start init-users)

(defstate ^:no-doc clients
  :start init-clients)

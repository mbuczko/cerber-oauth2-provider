(ns cerber.db
  (:require [cerber.config :refer [app-config]]
            [mount.core :as mount :refer [defstate]]
            [conman.core :as conman]))

(defn init-connection [config]
  (when config
    (Class/forName (:driver-class config))
    (conman/connect! config)))

(defn close-connection [db]
  (when db
    (conman/disconnect! db)))

(defstate ^:dynamic *db*
  :start (init-connection (:jdbc-pool app-config))
  :stop (close-connection *db*))

(conman/bind-connection *db*
                        "db/cerber/tokens.sql"
                        "db/cerber/clients.sql"
                        "db/cerber/authcodes.sql"
                        "db/cerber/users.sql"
                        "db/cerber/sessions.sql")

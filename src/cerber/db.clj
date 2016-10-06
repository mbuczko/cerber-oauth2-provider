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
  :start (init-connection (get-in app-config [:cerber :jdbc-pool]))
  :stop (close-connection *db*))

(conman/bind-connection *db*
                        "db/tokens.sql"
                        "db/clients.sql"
                        "db/authcodes.sql"
                        "db/users.sql"
                        "db/sessions.sql")

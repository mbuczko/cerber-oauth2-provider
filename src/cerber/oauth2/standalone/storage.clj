(ns cerber.oauth2.standalone.storage
  (:require [cerber.db :as db]
            [cerber.config :refer [app-config]]
            [mount.core :refer [defstate]]))

(defstate oauth-db
  :start (db/init-pool (:jdbc-spec app-config))
  :stop  (db/close-pool oauth-db))

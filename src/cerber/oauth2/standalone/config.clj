(ns cerber.oauth2.standalone.config
  (:require [cprop.core :as cprop]
            [cprop.source :refer [from-env from-resource from-system-props]]
            [failjure.core :as f]
            [mount.core :as mount :refer [defstate]]))

(defn load-resource
  "Loads single configuration resource.
  Returns empty map when resource was not found."

  [resource]
  (let [res (f/try* (from-resource resource))]
    (if (f/failed? res) {} res)))

(defn load-config
  "Loads configuration file depending on environment."

  [env]
  (println (str "Loading " env " environment..."))
  (cprop/load-config :resource "cerber.edn"
                     :merge [(load-resource (str "cerber-" env ".edn"))]))

(defn init-cerber []
  (load-config (or (:env (from-env))
                   (:env (from-system-props))
                   "local")))

(defstate app-config
  :start (init-cerber))

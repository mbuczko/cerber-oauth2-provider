(ns cerber.config
  (:require [cprop
             [core :as cprop]
             [source :refer [from-resource from-system-props from-env]]]
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
  (let [envs (from-env)]
    (load-config (or (:env envs) "local"))))

(defstate app-config
  :start (init-cerber))

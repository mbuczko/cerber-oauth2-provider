(ns cerber.config
  (:require [cprop
             [core :as cprop]
             [source :refer [from-resource from-system-props]]]
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
  (cprop/load-config :resource "cerber-default.edn"
                     :merge [(load-resource "cerber.edn")
                             (load-resource (str "cerber-" env ".edn"))]))

(defn init-cerber []
  (load-config (or (System/getenv "ENV") "local")))

(defstate app-config
  :start (init-cerber))

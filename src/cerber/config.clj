(ns cerber.config
  (:require [cprop
             [core :as cprop]
             [source :refer [from-resource from-system-props]]]
            [mount.core :refer [defstate] :as mount]))

(defn load-config
  "Loads configuration file depending on environment"
  [base-name env]

  (println "Loading\033[1;31m" env "\033[0mconfig...")
  (merge-with conj
              {:is-prod? (= env "prod")
               :is-test? (= env "test")
               :is-dev?  (or (= env "dev")
                             (= env "local"))}
              (cprop/load-config :resource (str base-name ".edn")
                                 :merge [(from-resource (str base-name "-" env ".edn"))
                                         (from-system-props)])))

(defn init-cerber [{:keys [base-name env]}]
  (load-config (or base-name "cerber") (or env "local")))

(defstate app-config
  :start (init-cerber (mount/args)))

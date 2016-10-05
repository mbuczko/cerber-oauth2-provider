(ns cerber.config
  (:require [helpful-loader.edn :as edn-loader]
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
              (edn-loader/load-one-or-nil (str base-name ".edn"))
              (edn-loader/load-one-or-nil (str base-name "-" env ".edn"))))

(defn init-cerber [{:keys [base-name env]}]
  (load-config (or base-name "cerber") (or env "local")))

(defstate app-config
  :start (init-cerber (mount/args)))

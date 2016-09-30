(ns cerber.config
  (:require [clojure.tools.logging :as log]
            [helpful-loader.edn :as edn-loader]
            [mount.core :refer [defstate] :as mount]))

(defn load-config
  "Loads configuration file depending on environment"
  [basename env]

  (log/info "Loading\033[1;31m" env "\033[0mconfig...")
  (merge-with conj
              {:is-prod? (= env "prod")
               :is-test? (= env "test")
               :is-dev?  (or (= env "dev")
                             (= env "local"))}
              (edn-loader/load-one-or-nil (str basename ".edn"))
              (edn-loader/load-one-or-nil (str basename "-" env ".edn"))))

(defstate app-config
  :start (let [{:keys [basename env]} (mount/args)]
           (load-config (or basename "cerber")
                        (or env "local"))))

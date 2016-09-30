(ns cerber.system
  (:require [clojure.tools.namespace.repl :as tn]
            [mount.core :refer [defstate] :as mount]
            [cerber.server]))

(defn go
  ([] (go {}))
  ([args] (mount/start-with-args args) :ready-with-args))

(defn stop []
  (mount/stop))

(defn reset []
  (stop)
  (tn/refresh :after 'cerber.system/go))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

(defn reset
  "Stops all states defined by defstate, reloads modified source files, and selectively restarts the states."
  []
  (stop)
  (tn/refresh :after 'cerber.system/go))

(ns cerber.oauth2.standalone.system
  (:require [clojure.tools.namespace.repl :as tn]
            [mount.core :refer [defstate] :as mount]
            [cerber.oauth2.standalone.server]))

(defn go
  "Starts entire system and inititalizes all states defined by mount's `defstate`."
  []
  (mount/start))

(defn stop
  "Stops the system and shuts downs all initialized states."
  []
  (mount/stop))

(defn reset
  "Resets the system by stopping it first and starting again."
  []
  (stop)
  (tn/refresh :after 'cerber.oauth2.standalone.system/go))

(defn refresh
  []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

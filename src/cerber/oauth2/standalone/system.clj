(ns cerber.oauth2.standalone.system
  (:require [clojure.tools.namespace.repl :as tn]
            [mount.core :refer [defstate] :as mount]
            [cerber.oauth2.standalone.server]))

(defn go []
  (mount/start))

(defn stop []
  (mount/stop))

(defn reset []
  (stop)
  (tn/refresh :after 'cerber.oauth2.standalone.system/go))

(defn refresh []
  (stop)
  (tn/refresh))

(defn refresh-all []
  (stop)
  (tn/refresh-all))

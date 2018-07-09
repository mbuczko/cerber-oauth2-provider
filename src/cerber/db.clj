(ns cerber.db
  (:require [cerber.helpers :as helpers]
            [conman.core :as conman]))

(defn bind-queries
  [db-conn]
  (binding [*ns* (the-ns 'cerber.db)]
    (conman/bind-connection db-conn
                            "db/cerber/tokens.sql"
                            "db/cerber/clients.sql"
                            "db/cerber/authcodes.sql"
                            "db/cerber/users.sql"
                            "db/cerber/sessions.sql")))

;; helper functions to call SQL queries as interned functions

(defn sql-fn
  [fn-sym]
  (get (ns-interns 'cerber.db) fn-sym))

(defn sql-call
  [fn-sym & args]
  (when-let [sqfn (sql-fn fn-sym)]
    (apply sqfn args)))

;; helper functions to manage with SQL queries called periodically

(def scheduler ^java.util.concurrent.ScheduledExecutorService
  (java.util.concurrent.Executors/newScheduledThreadPool 1))

(defn make-periodic
  "Schedules a SQL query (a function) to be run periodically at
   given interval. Function gets {:date now} as an argument."

  [fn-sym interval]
  (when-let [sqfn (sql-fn fn-sym)]
    (let [runnable (proxy [Runnable] []
                     (run [] (sqfn {:date (helpers/now)})))]
      (.scheduleAtFixedRate scheduler ^Runnable runnable 0 interval java.util.concurrent.TimeUnit/MILLISECONDS))))

(defn stop-periodic
  "Stops periodically run function created by `make-periodic`."

  [^java.util.concurrent.ScheduledFuture periodic]
  (when periodic
    (.cancel periodic false)))

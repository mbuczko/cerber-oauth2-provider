(ns cerber.migration
  (:require [mbuczko.boot-flyway :refer [flyway]]))

(def mysql-opts
  ["-d" "com.mysql.cj.jdbc.Driver" "-o" "locations=db/migrations/mysql" "-j"])

(def pgsql-opts
  ["-d" "org.postgresql.Driver" "-o" "locations=db/migrations/postgres" "-j"])

(defn migrate [jdbc-url]
  (apply flyway (if-not jdbc-url ["-i"] (conj (if (.startsWith jdbc-url "jdbc:postgresql")
                                                pgsql-opts
                                                mysql-opts) jdbc-url "-m"))))

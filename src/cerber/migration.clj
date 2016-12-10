(ns cerber.migration
  (:require [mbuczko.boot-flyway :refer [flyway]]))

(def mysql-opts
  ["-d" "com.mysql.cj.jdbc.Driver" "-o" "locations=db/migrations/mysql" "-j"])

(def pgsql-opts
  ["-d" "org.postgresql.Driver" "-o" "locations=db/migrations/postgres" "-j"])

(defn migrate [jdbc-url action]
  (let [args (if (.startsWith jdbc-url "jdbc:postgresql") pgsql-opts mysql-opts)]
    (apply flyway (conj args jdbc-url (condp = action
                                        :migrate "-m"
                                        :clean "-c"
                                        "-i")))))

(ns cerber.migration
  (:require [mbuczko.boot-flyway :refer [flyway]]
            [clojure.string :as str]))


(def ^:const ^:private opts
  {"postgresql" ["-d" "org.postgresql.Driver" "-o" "locations=db/migrations/postgres" "-j"]
   "mysql"      ["-d" "com.mysql.cj.jdbc.Driver" "-o" "locations=db/migrations/mysql" "-j"]})

(defn- db-opts [jdbc-url]
  (let [db-type (second (str/split jdbc-url #":"))]
    (println (str "Migrating database: " db-type))
    (or (get opts db-type)
        (throw (Exception. "Unsupported database. Check jdbc-url.")))))

(defn migrate
  "Migrates schema changes to database.

  Database and corresponding migration files are chosen based on `jdbc-url`, eg. following jdbc-url
  launches migration of postgres schema:

      jdbc:postgresql://localhost:5432/template1?user=postgres

  `action` may be either \"start\" which immediately starts migration, \"clear\" which clears entire
  schema (use with caution!) or \"info\" which displays the status of all migrations proceeded so far.

  No action assumes \"start\" by default and starts the migration process."

  [jdbc-url & [action]]
  (apply flyway (conj (db-opts jdbc-url) jdbc-url (condp = action
                                                    "clear" "-c"
                                                    "info"  "-i"
                                                    "start" "-m"
                                                    "-m"))))

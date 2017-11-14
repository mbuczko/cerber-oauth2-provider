(ns cerber.migration
  (:require [mbuczko.boot-flyway :refer [flyway]]
            [clojure.string :as str]))


(def ^:const opts
  {"postgresql" ["-d" "org.postgresql.Driver" "-o" "locations=db/migrations/postgres" "-j"]
   "mysql"      ["-d" "com.mysql.cj.jdbc.Driver" "-o" "locations=db/migrations/mysql" "-j"]})

(defn db-opts [jdbc-url]
  (let [db-type (second (str/split jdbc-url #":"))]
    (println (str "Migrating database: " db-type))
    (or (get opts db-type)
        (throw (Exception. "Unsupported database. Check jdbc-url.")))))

(defn migrate [jdbc-url & [action]]
  (apply flyway (conj (db-opts jdbc-url) jdbc-url (condp = action
                                                    "clean" "-c"
                                                    "info"  "-i"
                                                    "-m"))))

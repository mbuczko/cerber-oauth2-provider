(ns cerber.core
  (:gen-class)
  (:require [mbuczko.flyway :as flyway]
            [clojure.tools.cli :refer [parse-opts]]))

(def cli-options
  ;; An option with a required argument
  [["-d" "--driver CLASS" "database driver" :default "com.mysql.cj.jdbc.Driver"]
   ["-u" "--user USER" "user used to connect"]
   ["-p" "--password PASS" "password used to connect"]
   ["-j" "--url URL" "jdbc URL"]
   ["-c" "--clean" "drop all objects in schema"]
   ["-m" "--migrate" "apply pending migrations"]])

(defn -main [& args]
  (let [{:keys [driver url user password clean migrate]} (:options (parse-opts args cli-options))
        dataset {:driver driver
                 :url url
                 :user user
                 :password password
                 :locations ["db/migrations"]}
        config  (flyway/flyway dataset)]

    (if clean
      (flyway/clean config)
      (if migrate
        (flyway/migrate config)
        (flyway/info config)))))

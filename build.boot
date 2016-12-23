(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :directories    #{"config"}
 :dependencies '[[org.clojure/clojure "1.8.0"]
                 [com.taoensso/carmine "2.15.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [mbuczko/boot-flyway "0.1.1"]
                 [ring/ring-defaults "0.3.0-beta1"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [zilti/boot-midje "0.2.1-SNAPSHOT" :scope "test"]
                 [com.github.kstyrc/embedded-redis "0.6" :scope "test"]
                 [com.h2database/h2 "1.4.193" :scope "test"]
                 [mysql/mysql-connector-java "6.0.5" :scope "test"]
                 [org.postgresql/postgresql "9.4.1212" :scope "test"]
                 [midje "1.8.3" :scope "test"]
                 [peridot "0.4.4" :scope "test"]
                 [compojure "1.6.0-beta1" :scope "test"]
                 [http-kit "2.2.0" :scope "test"]
                 [helpful-loader "0.1.1"]
                 [conman "0.6.2"]
                 [mount "0.1.11"]
                 [crypto-random "1.2.0"]
                 [selmer "1.10.3"]
                 [failjure "0.1.4"]
                 [ring-anti-forgery "0.3.0"]
                 [ring-middleware-format "0.7.0"]
                 [digest "1.4.5"]])

(def +version+ "0.1.7")

;; to check the newest versions:
;; boot -d boot-deps ancient

(require
 '[cerber.oauth2.system]
 '[cerber.migration]
 '[adzerk.bootlaces    :refer [bootlaces! build-jar push-release]]
 '[zilti.boot-midje    :refer [midje]])

(bootlaces! +version+)

;; which source dirs should be monitored for changes when resetting app?
(apply clojure.tools.namespace.repl/set-refresh-dirs (get-env :source-paths))


(deftask go
  "Starts system initializing all defined states."
  [e env ENVIRONMENT str "Environment to use while starting application up."]
  (cerber.oauth2.system/go {:env (or env (get-env :env) "local")
                            :base-name "cerber"}))

(deftask test
  "Environment for test-driven development."
  []
  (set-env! :env "test")
  (comp (watch)
        (midje)
        (speak)))

(deftask reset
  "Restarts system using local environment."
  []
  (cerber.oauth2.system/reset))

(deftask migrate
  "Applies pending migrations."
  [j jdbc-url URL str "JDBC url"]
  (cerber.migration/migrate jdbc-url))

(task-options! midje {:test-paths #{"test"}}
               pom   {:project 'cerber/cerber-oauth2-provider
                      :version +version+
                      :description "OAuth2 provider"
                      :url "https://github.com/mbuczko/cerber-oauth2-provider"
                      :scm {:url "https://github.com/mbuczko/cerber-oauth2-provider"}})

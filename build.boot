(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :directories    #{"config"}
 :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                 [com.taoensso/carmine "2.16.0"]
                 [org.mindrot/jbcrypt "0.4"]
                 [mbuczko/boot-flyway "0.1.1" :scope "test"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [zilti/boot-midje "0.2.1-SNAPSHOT" :scope "test"]
                 [com.github.kstyrc/embedded-redis "0.6" :scope "test"]
                 [com.h2database/h2 "1.4.196" :scope "test"]
                 [mysql/mysql-connector-java "6.0.6" :scope "test"]
                 [org.postgresql/postgresql "42.1.4" :scope "test"]
                 [ring/ring-defaults "0.3.1"]
                 [midje "1.8.3" :scope "test"]
                 [peridot "0.5.0" :scope "test"]
                 [compojure "1.6.0" :scope "test"]
                 [http-kit "2.2.0" :scope "test"]
                 [cprop "0.1.11"]
                 [conman "0.6.8"]
                 [mount "0.1.11"]
                 [crypto-random "1.2.0"]
                 [selmer "1.11.1"]
                 [failjure "1.2.0"]
                 [ring-anti-forgery "0.3.0"]
                 [ring-middleware-format "0.7.2"]
                 [digest "1.4.6"]])

(def +version+ "0.1.10")

;; to check the newest versions:
;; boot -d boot-deps ancient

(require
 '[cerber.oauth2.standalone.system]
 '[cerber.migration]
 '[adzerk.bootlaces    :refer [bootlaces! build-jar push-release]]
 '[zilti.boot-midje    :refer [midje]])

(bootlaces! +version+)

;; which source dirs should be monitored for changes when resetting app?
(apply clojure.tools.namespace.repl/set-refresh-dirs (get-env :source-paths))


(deftask go
  []
  (cerber.oauth2.standalone.system/go))

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
  (cerber.oauth2.standalone.system/reset))

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

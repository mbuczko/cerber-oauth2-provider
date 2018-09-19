(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :directories    #{"config"}
 :dependencies '[[org.clojure/clojure "1.9.0" :scope "provided"]
                 [com.taoensso/carmine "2.18.1"]
                 [org.mindrot/jbcrypt "0.4"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [zilti/boot-midje "0.2.2-SNAPSHOT" :scope "test"]
                 [com.h2database/h2 "1.4.197" :scope "test"]
                 [mysql/mysql-connector-java "8.0.11" :scope "test"]
                 [org.postgresql/postgresql "42.2.4" :scope "test"]
                 [com.github.kstyrc/embedded-redis "0.6" :scope "test"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-anti-forgery "1.3.0"]
                 [midje "1.9.2" :scope "test"]
                 [peridot "0.5.1" :scope "test"]
                 [compojure "1.6.1" :scope "test"]
                 [http-kit "2.3.0" :scope "test"]
                 [cprop "0.1.11" :scope "test"]
                 [mount "0.1.12" :scope "test"]
                 [conman "0.7.4"]
                 [crypto-random "1.2.0"]
                 [selmer "1.11.8"]
                 [failjure "1.3.0"]
                 [ring-middleware-format "0.7.2"]
                 [digest "1.4.8"]])

(def +version+ "1.0.1")

;; to check the newest versions:
;; boot -d boot-deps ancient

(require
 '[cerber.oauth2.standalone.system]
 '[adzerk.bootlaces :refer [bootlaces! build-jar push-release push-snapshot]]
 '[zilti.boot-midje :refer [midje]])

(bootlaces! +version+)

;; which source dirs should be monitored for changes when resetting app?
(apply clojure.tools.namespace.repl/set-refresh-dirs (get-env :source-paths))

(deftask go
  []
  (cerber.oauth2.standalone.system/go))

(deftask reset
  []
  (cerber.oauth2.standalone.system/reset))

(deftask tests
  "Environment for test-driven development."
  []
  (comp (watch)
        (midje)
        (speak)))

(task-options! midje {:test-paths #{"test"}}
               pom   {:project 'cerber/cerber-oauth2-provider
                      :version +version+
                      :description "OAuth2 provider"
                      :url "https://github.com/mbuczko/cerber-oauth2-provider"
                      :scm {:url "https://github.com/mbuczko/cerber-oauth2-provider"}})

(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"resources"}
 :directories    #{"config"}
 :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/tools.logging "0.3.1"]
                 [com.taoensso/carmine "2.14.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [mbuczko/boot-flyway "0.1.0-SNAPSHOT"]
                 [ring/ring-defaults "0.3.0-beta1"]
                 [adzerk/bootlaces "0.1.13" :scope "test"]
                 [zilti/boot-midje "0.2.1-SNAPSHOT" :scope "test"]
                 [com.github.kstyrc/embedded-redis "0.6" :scope "test"]
                 [com.h2database/h2 "1.4.192"]
                 [midje "1.8.3" :scope "test"]
                 [helpful-loader "0.1.1"]
                 [conman "0.6.0"]
                 [mount "0.1.10"]
                 [compojure "1.5.0"]
                 [crypto-random "1.2.0"]
                 [slingshot "0.12.2"]
                 [http-kit "2.2.0"]
                 [selmer "1.0.7"]
                 [compojure "1.6.0-beta1"]
                 [cheshire "5.6.3"]
                 [failjure "0.1.3"]
                 [ring-anti-forgery "0.3.0"]
                 [ring-middleware-format "0.7.0"]])

(def +version+ "0.1.0")

;; to check the newest versions:
;; boot -d boot-deps ancient

(require
 '[cerber.system]
 '[adzerk.bootlaces    :refer [bootlaces! build-jar push-release]]
 '[zilti.boot-midje    :refer [midje]]
 '[mbuczko.boot-flyway :refer [flyway]])

(bootlaces! +version+)

;; which source dirs should be monitored for changes when resetting app?
(apply clojure.tools.namespace.repl/set-refresh-dirs (get-env :source-paths))


(deftask dev
  "Bunch of tasks making things easier in development mode."
  []
  (comp (watch)
        (midje)
        (speak)))

(deftask uberjar
  "Create an uberjar."
  []
  (comp (aot)
        (uber)
        (jar)))

(deftask reset
  "Restarts system using local environment."
  []
  (cerber.system/reset))

(deftask go
  "Starts system initializing all defined states."
  [e env ENVIRONMENT str "Environment to use while starting application up."]
  (cerber.system/go {:env (or env "local")
                     :basename "cerber"}))

(task-options! midje  {:test-paths #{"test"}}
               flyway {:driver "org.postgresql.Driver"
                       :url "jdbc:postgresql://localhost:5432/template1?user=postgres"}
               pom    {:project 'mbuczko/clj-oauth2-provider
                       :version +version+
                       :description "OAuth2 provider"
                       :url "https://github.com/mbuczko/clj-oauth2-provider"
                       :scm {:url "https://github.com/mbuczko/clj-oauth2-provider"}})

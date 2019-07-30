(ns cerber.stores.client-test
  (:require [cerber.oauth2.core :as core]
            [cerber.test-utils :refer [instance-of has-secret with-storage]]
            [midje.sweet :refer [fact tabular => =not=> contains just truthy]])
  (:import cerber.error.HttpError
           cerber.stores.client.Client))

(def info "testing client")
(def scopes ["photo:read"])
(def grants ["authorization_code" "password"])
(def redirects ["http://localhost" "http://defunkt.pl"])

(tabular
 (fact "Redirect URIs must be a valid URLs with no forbidden characters."
       (with-storage :in-memory
         (core/create-client grants ?redirects
                             :info info
                             :scopes scopes
                             :enabled? true
                             :approved? false) => ?expected))

 ?redirects                       ?expected
 ["http://foo.bar.bazz"]          truthy
 ["foo.bar" "http://bar.foo.com"] (instance-of HttpError)
 ["http://foo.bar" ""]            (instance-of HttpError)
 ["http://foo.bar#bazz"]          (instance-of HttpError)
 ["http://some.nasty/../hack"]    (instance-of HttpError)
 ["http://some nasty.hack"]       (instance-of HttpError)
 ["http://some\tvery.nasty.hack"] (instance-of HttpError))

(fact "Created client has a secret code."
       (with-storage :in-memory

         ;; given
         (let [client (core/create-client grants redirects
                                          :info info
                                          :scopes scopes
                                          :enabled? true
                                          :approved? false)]
           ;; then
           client => (instance-of Client)
           client => (has-secret :secret))))

(tabular
 (fact "Clients are stored in a correct model."
       (with-storage ?storage

         ;; given
         (let [created (core/create-client grants redirects
                                           :info info
                                           :scopes scopes
                                           :enabled? true
                                           :approved? false)
               client  (core/find-client (:id created))]

           ;; then
           client => (instance-of Client)
           client => (has-secret :secret)
           client => (contains {:id (:id client)
                                :info info
                                :redirects redirects
                                :grants grants
                                :scopes scopes
                                :enabled? true
                                :approved? false}))))

 ?storage :in-memory :redis :sql)

(tabular
 (fact "Revoked client is not returned from store."
       (with-storage ?storage

         ;; given
         (let [client (core/create-client grants redirects
                                          :info info
                                          :scopes scopes
                                          :enabled? true
                                          :approved? false)
               client-id (:id client)]

           ;; when
           (core/delete-client client-id)

           ;; then
           (core/find-client client-id) => nil)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Revoked client has all its tokens revoked as well."
       (with-storage ?storage

         ;; given
         (let [client (core/create-client grants redirects
                                          :info info
                                          :scopes scopes
                                          :enabled? true
                                          :approved? false)
               client-id (:id client)]

           ;; when
           (core/delete-client client-id)

           ;; then
           (core/find-refresh-tokens client-id) => (just '()))))

 ?storage :in-memory :sql :redis)

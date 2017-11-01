(ns cerber.stores.client-test
  (:require [cerber.common-test :refer :all]
            [cerber.stores.client :refer :all]
            [midje.sweet :refer :all])
  (:import cerber.error.HttpError
           cerber.stores.client.Client))

(def redirects ["http://localhost" "http://defunkt.pl"])
(def scope ["photo:read"])
(def grants [])
(def info "testing client")

(fact "New client is returned as Client record with secret filled in."
      (with-client-store (create-client-store :in-memory)
        (let [client (create-client info redirects scope grants false)]
          client => (instance-of Client)
          client => (has-secret :secret))))

(tabular
 (fact "Redirect URIs must be a valid URLs with no forbidden characters."
       (with-client-store (create-client-store :in-memory)
         (create-client info ?redirects scope grants false) => ?expected))

 ?redirects                       ?expected
 ["http://dupa.z.trupa"]          truthy
 ["foo.bar" "http://bar.foo.com"] (instance-of HttpError)
 ["http://foo.bar" ""]            (instance-of HttpError)
 ["http://some.nasty/../hack.pl"] (instance-of HttpError)
 ["http://some nasty.pl"]         (instance-of HttpError))

(tabular
 (fact "Newly created client is returned when stored correctly in a store."
       (with-client-store (create-client-store ?store)
         (purge-clients)

         ;; given
         (let [client (create-client info redirects scope grants false)
               found  (find-client (:id client))]

           ;; then
           found => (instance-of Client)
           found => (has-secret :secret))))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Revoked client is not returned from store."
       (with-client-store (create-client-store ?store)
         (purge-clients)

         ;; given
         (let [client (create-client info redirects scope grants false)
               id (:id client)]

           ;; and
           (find-client id) => (instance-of Client)

           ;; when
           (revoke-client id)

           ;; then
           (find-client id) => nil)))

 ?store :in-memory :sql :redis)

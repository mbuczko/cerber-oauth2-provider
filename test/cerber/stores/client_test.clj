(ns cerber.stores.client-test
  (:require [cerber.oauth2.common :refer :all]
            [cerber.stores.client :refer :all]
            [midje.sweet :refer :all])
  (:import cerber.error.HttpError
           cerber.stores.client.Client))

(def homepage "http://ooops.com")

(def redirects ["http://localhost" "http://defunkt.pl"])
(def authorities [])
(def grants [])
(def scopes ["photo"])

(fact "New client is returned as Client record with secret filled in."
      (with-client-store (create-client-store :in-memory)
        (let [client (create-client homepage redirects scopes grants authorities)]
          client => (instance-of Client)
          client => (has-secret :secret))))

(tabular
 (fact "Redirect URIs must be a valid URLs with no forbidden characters."
       (with-client-store (create-client-store :in-memory)
         (create-client homepage ?redirects scopes grants authorities) => ?expected))

 ?redirects                       ?expected
 ["http://dupa.z.trupa"]          truthy
 ["foo.bar" "http://bar.foo.com"] (instance-of HttpError)
 ["http://foo.bar" ""]            (instance-of HttpError)
 ["http://some.nasty/../hack.pl"] (instance-of HttpError)
 ["http://some nasty.pl"]         (instance-of HttpError))

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Newly created client is returned when stored correctly in a store."
         (with-client-store (create-client-store ?store)
           (purge-clients)
           (let [client (create-client homepage redirects scopes grants authorities)
                 found  (find-client (:id client))]
             found => (instance-of Client)
             found => (has-secret :secret)))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Revoked client is not returned from store."
         (with-client-store (create-client-store ?store)
           (purge-clients)

           (let [client (create-client homepage redirects scopes grants authorities)
                 id (:id client)]
             (find-client id) => (instance-of Client)
             (revoke-client id)
             (find-client id) => nil))))

 ?store :in-memory :sql :redis)

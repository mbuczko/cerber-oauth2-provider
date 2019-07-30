(ns cerber.stores.authcode-test
  (:require [cerber.stores.authcode :as a]
            [cerber.test-utils :refer [instance-of has-secret create-test-user create-test-client with-storage]]
            [midje.sweet :refer [fact tabular => =not=> contains just truthy]])
  (:import cerber.stores.authcode.AuthCode))

(def redirect-uri "http://localhost")
(def scope "photo:read")

(fact "Created authcode has a secret code."
      (with-storage :in-memory

        ;; given
        (let [user     (create-test-user)
              client   (create-test-client redirect-uri :scope scope)
              authcode (a/create-authcode client user scope redirect-uri)]

          ;; then
          authcode => (instance-of AuthCode)
          authcode => (has-secret :code))))

(tabular
 (fact "Authcodes are stored in a correct model."
       (with-storage ?storage

         ;; given
         (let [user    (create-test-user)
               client  (create-test-client redirect-uri :scope scope)
               created (a/create-authcode client user scope redirect-uri)]

           ;; then
           (let [authcode (a/find-authcode (:code created))]

             authcode => (instance-of AuthCode)
             authcode => (has-secret :code)
             authcode => (contains {:client-id (:id client)
                                    :login (:login user)
                                    :scope scope
                                    :redirect-uri redirect-uri})

             (:expires-at authcode)) =not=> nil)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Revoked authcode is not returned from store."
       (with-storage ?storage

         ;; given
         (let [user     (create-test-user)
               client   (create-test-client redirect-uri :scope scope)
               authcode (a/create-authcode client user scope redirect-uri)]

           ;; then
           (a/find-authcode (:code authcode)) => (instance-of AuthCode)
           (a/revoke-authcode authcode)
           (a/find-authcode (:code authcode)) => nil)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Expired authcodes are removed from store."
       (with-storage ?storage

         ;; given
         (let [user     (create-test-user)
               client   (create-test-client redirect-uri :scope scope)
               authcode (a/create-authcode client user scope redirect-uri -1)]

           ;; then
           (a/find-authcode (:code authcode))) => nil))

 ?storage :in-memory :sql :redis)

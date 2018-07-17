(ns cerber.stores.authcode-test
  (:require [cerber.stores.authcode :refer :all]
            [cerber.test-utils :refer [instance-of has-secret create-test-user create-test-client with-stores]]
            [midje.sweet :refer :all])
  (:import cerber.stores.authcode.AuthCode))

(def redirect-uri "http://localhost")
(def scope "photo:read")

(fact "Created authcode has a secret code."
      (with-stores :in-memory

        ;; given
        (let [user     (create-test-user "")
              client   (create-test-client scope redirect-uri)
              authcode (create-authcode client user scope redirect-uri)]

          ;; then
          authcode => (instance-of AuthCode)
          authcode => (has-secret :code))))

(tabular
 (fact "Authcodes are stored in a correct model."
       (with-stores ?store

         ;; given
         (let [user    (create-test-user "")
               client  (create-test-client scope redirect-uri)
               created (create-authcode client user scope redirect-uri)]

           ;; then
           (let [authcode (find-authcode (:code created))]

             authcode => (instance-of AuthCode)
             authcode => (has-secret :code)
             authcode => (contains {:client-id (:id client)
                                    :login (:login user)
                                    :scope scope
                                    :redirect-uri redirect-uri})

             (:expires-at authcode)) =not=> nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Revoked authcode is not returned from store."
       (with-stores ?store

         ;; given
         (let [user     (create-test-user "")
               client   (create-test-client scope redirect-uri)
               authcode (create-authcode client user scope redirect-uri)]

           ;; then
           (find-authcode (:code authcode)) => (instance-of AuthCode)
           (revoke-authcode authcode)
           (find-authcode (:code authcode)) => nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Expired authcodes are removed from store."
       (with-stores ?store

         ;; given
         (let [user     (create-test-user "")
               client   (create-test-client scope redirect-uri)
               authcode (create-authcode client user scope redirect-uri -1)]

           ;; then
           (find-authcode (:code authcode))) => nil))

 ?store :in-memory :sql :redis)

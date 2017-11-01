(ns cerber.stores.authcode-test
  (:require [cerber.stores.authcode :refer :all]
            [cerber.common-test :refer :all]
            [midje.sweet :refer :all])
  (:import cerber.stores.authcode.AuthCode))

(def redirect-uri "http://localhost")
(def scope "photo:read")
(def user (create-test-user "nioh" "alamakota"))
(def client (create-test-client scope redirect-uri))

(fact "Newly created authcode is returned with secret code filled in."
      (with-authcode-store (create-authcode-store :in-memory)

        ;; given
        (let [authcode (create-authcode client user scope redirect-uri)]

          ;; then
          authcode => (instance-of AuthCode)
          authcode => (has-secret :code))))

(tabular
 (fact "Authcode found in a store is returned with secret code filled in."
       (with-authcode-store (create-authcode-store ?store)
         (purge-authcodes)

         ;; given
         (let [authcode (create-authcode client user scope redirect-uri)
               found (find-authcode (:code authcode))]

           ;; then
           found => (instance-of AuthCode)
           found => (has-secret :code))))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Revoked authcode is not returned from store."
       (with-authcode-store (create-authcode-store ?store)
         (purge-authcodes)

         ;; given
         (let [authcode (create-authcode client user scope redirect-uri)]

           ;; then
           (find-authcode (:code authcode)) => (instance-of AuthCode)
           (revoke-authcode authcode)
           (find-authcode (:code authcode)) => nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Expired authcodes are removed from store."
       (with-authcode-store (create-authcode-store ?store)
         (purge-authcodes)

         ;; given
         (let [authcode (create-authcode client user scope redirect-uri -1)]

           ;; then
           (find-authcode (:code authcode))) => nil))

 ?store :in-memory :sql :redis)

(ns cerber.stores.user-test
  (:require [midje.sweet :refer :all]
            [cerber.common-test :refer :all]
            [cerber.stores.user :refer :all])
  (:import  [cerber.stores.user User]))

(fact "Newly created user is returned with auto-generated id and crypted password filled in."
      (with-user-store (create-user-store :in-memory)

        ;; given
        (let [pass "alamakota"
              user (create-user {:login "foo"} pass)]

          ;; then
          user => (instance-of User)
          user => (has-secret :password)

          ;; auto-generated identity
          (:id user) => truthy

          ;; password must be encrypted!
          (= pass (:password user)) => false

          ;; hashed passwords should be the same
          (valid-password? pass (:password user)) => true)))

(fact "Newly created user is enabled by default if no :enabled? property was set."
      (with-user-store (create-user-store :in-memory)

        ;; given
        (let [user1 (create-user {:login "foo"} "aa")
              user2 (create-user {:login "bar"
                                  :enabled? false} "bb")]

          ;; then
          (:enabled? user1) => true
          (:enabled? user2) => false)))

(tabular
 (fact "User found in a store is returned with details filled in."
       (with-user-store (create-user-store ?store)
         (purge-users)

         ;; given
         (create-user {:login "foo"} "alamakota")

         ;; when
         (let [user (find-user "foo")]

           ;; then
           user => (instance-of User)
           user => (has-secret :password))))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Revoked user is not returned from store."
       (with-user-store (create-user-store ?store)
         (purge-users)

         ;; given
         (let [user (create-user {:login "foo"} "alamakota")]
           (find-user "foo") => (instance-of User)

           ;; when
           (revoke-user "foo")

           ;; then
           (find-user "foo") => nil)))

 ?store :in-memory :sql :redis)

(ns cerber.stores.user-test
  (:require [cerber.stores.user :refer :all]
            [cerber.test-utils :refer [has-secret instance-of with-stores]]
            [midje.sweet :refer :all])
  (:import cerber.stores.user.User))

(def login "foo")
(def password "pass")

(fact "Newly created user is returned with auto-generated id and crypted password filled in."
      (with-stores :in-memory

        ;; given
        (let [user (create-user {:login login} password)]

          ;; then
          user => (instance-of User)
          user => (has-secret :password)

          ;; auto-generated identity
          (:id user) => truthy

          ;; password must be encrypted!
          (= password (:password user)) => false

          ;; hashed passwords should be the same
          (valid-password? password (:password user)) => true)))

(fact "Newly created user is enabled by default if no :enabled? property was set."
      (with-stores :in-memory

        ;; given
        (let [user1 (create-user {:login login} password)
              user2 (create-user {:login "bazz" :enabled? false} password)]

          ;; then
          (:enabled? user1) => true
          (:enabled? user2) => false)))

(tabular
 (fact "User found in a store is returned with details filled in."
       (with-stores ?store

         ;; given
         (create-user {:login login} password)

         ;; when
         (let [user (find-user login)]

           ;; then
           user => (instance-of User)
           user => (has-secret :password))))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Revoked user is not returned from store."
       (with-stores ?store

         ;; given
         (let [user (create-user {:login login} password)]
           (find-user login) => (instance-of User)

           ;; when
           (revoke-user user)

           ;; then
           (find-user login) => nil)))

 ?store :in-memory :sql :redis)

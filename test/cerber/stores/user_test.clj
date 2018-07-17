(ns cerber.stores.user-test
  (:require [cerber.test-utils :refer [has-secret instance-of with-stores]]
            [cerber.stores.user :refer [valid-password?]]
            [cerber.oauth2.core :as core]
            [midje.sweet :refer :all])
  (:import cerber.stores.user.User))

(def login "foo")
(def email "foo@bazz.bar")
(def uname "Foo Bazz")
(def password "pass")

(fact "Created user has auto-generated id and crypted password filled in."
      (with-stores :in-memory

        ;; given
        (let [user (core/create-user {:login login} password)]

          ;; then
          user => (instance-of User)
          user => (has-secret :password)

          ;; auto-generated identity
          (:id user) => truthy

          ;; password must be encrypted!
          (= password (:password user)) => false

          ;; hashed passwords should be the same
          (valid-password? password (:password user)) => true)))

(tabular
 (fact "Users are stored in a correct model, enabled by default if no :enabled? property was set."
       (with-stores ?store

         ;; given
         (let [created1 (core/create-user {:login login :email email :name uname} password)
               created2 (core/create-user {:login "bazz" :enabled? false} password)]

           ;; when
           (let [user1 (core/find-user (:login created1))
                 user2 (core/find-user (:login created2))]

             ;; then
             user1 => (has-secret :password)
             user1 => (contains {:login login
                                 :email email
                                 :name  uname
                                 :enabled? true})

             (:enabled? user2)) => false)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Revoked user is not returned from store."
       (with-stores ?store

         ;; given
         (let [user (core/create-user {:login login} password)]
           (core/find-user login) => (instance-of User)

           ;; when
           (core/delete-user login)

           ;; then
           (core/find-user login) => nil)))

 ?store :in-memory :sql :redis)

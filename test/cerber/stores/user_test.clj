(ns cerber.stores.user-test
  (:require [cerber.stores.user :refer :all]
            [cerber.test-utils :refer [has-secret instance-of]]
            [midje.sweet :refer :all])
  (:import cerber.stores.user.User))

(def login "foo")
(def password "pass")

(defmacro with-user-store
  [store & body]
  `(binding [*user-store* ~(atom store)] ~@body))

(fact "Newly created user is returned with auto-generated id and crypted password filled in."
      (with-user-store (create-user-store :in-memory)
        (purge-users)

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
      (with-user-store (create-user-store :in-memory)
        (purge-users)

        ;; given
        (let [user1 (create-user {:login login} password)
              user2 (create-user {:login "bazz" :enabled? false} password)]

          ;; then
          (:enabled? user1) => true
          (:enabled? user2) => false)))

(tabular
 (fact "User found in a store is returned with details filled in."
       (with-user-store (create-user-store ?store)
         (purge-users)

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
       (with-user-store (create-user-store ?store)
         (purge-users)

         ;; given
         (let [user (create-user {:login login} password)]
           (find-user login) => (instance-of User)

           ;; when
           (revoke-user user)

           ;; then
           (find-user login) => nil)))

 ?store :in-memory :sql :redis)

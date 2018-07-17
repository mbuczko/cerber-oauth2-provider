(ns cerber.stores.token-test
  (:require [cerber.helpers :as helpers]
            [cerber.oauth2.core :as core]
            [cerber.stores.token :refer [create-token generate-access-token]]
            [cerber.test-utils :refer [has-secret instance-of create-test-user create-test-client with-stores]]
            [midje.sweet :refer :all]
            [cerber.oauth2.settings :as settings])
  (:import cerber.stores.token.Token
           cerber.error.HttpError))

(def redirect-uri "http://localhost")
(def scope "photo:read")

(tabular
 (fact "Generated access-token contains required fields."
       (with-stores ?store

         ;; given
         (let [user    (create-test-user "")
               client  (create-test-client scope redirect-uri)
               token   (generate-access-token client user scope true)]

           ;; then
           token => (has-secret :access_token)
           token => (contains {:scope scope
                               :token_type "Bearer"
                               :expires_in (settings/token-valid-for)}))))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Access-token points to matching refresh-token."
       (with-stores ?store

         ;; given
         (let [user    (create-test-user "")
               client  (create-test-client scope redirect-uri)
               token   (generate-access-token client user scope true)
               refresh (first (core/find-refresh-tokens (:id client)))]

           ;; then
           (helpers/digest (:refresh_token token)) => (:secret refresh))))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Access- and refresh-tokens are stored in a correct model."
       (with-stores ?store

         ;; given
         (let [user    (create-test-user "")
               client  (create-test-client scope redirect-uri)
               token   (generate-access-token client user scope true)
               access  (core/find-access-token (:access_token token))
               refresh (first (core/find-refresh-tokens (:id client)))]

           ;; then
           access => (instance-of Token)
           access => (has-secret :secret)
           access => (contains {:client-id (:id client)
                                :user-id (:id user)
                                :login (:login user)
                                :scope scope})

           refresh => (instance-of Token)
           refresh => (has-secret :secret)
           refresh => (contains {:client-id (:id client)
                                 :user-id (:id user)
                                 :login (:login user)
                                 :scope scope})

           (:expires-at access) =not=> nil
           (:expires-at refresh) => nil)))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Revoked access-token is not returned from store."
       (with-stores ?store

         ;; given
         (let [user   (create-test-user "")
               client (create-test-client scope redirect-uri)
               token  (generate-access-token client user scope true)
               secret (:access_token token)]

           ;; when
           (core/find-access-token secret) => (instance-of Token)
           (core/revoke-access-token secret)

           ;; then
           (core/find-access-token secret) => nil)))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Revoked client tokens are not returned from store."
       (with-stores ?store

         ;; given
         (let [user   (create-test-user "")
               client (create-test-client scope redirect-uri)
               token  (generate-access-token client user scope true)
               secret (:access_token token)]

           (core/find-access-token secret) => (instance-of Token)
           (first (core/find-refresh-tokens (:id client))) => (instance-of Token)

           ;; when
           (core/revoke-client-tokens (:id client))

           ;; then
           (core/find-access-token secret) => nil
           (core/find-refresh-tokens (:id client)) => (just '()))))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Revoked client tokens for given user are not returned from store."
       (with-stores ?store

         ;; given
         (let [user1  (create-test-user "")
               user2  (create-test-user "")
               client (create-test-client scope redirect-uri)
               token1 (generate-access-token client user1 scope true)
               token2 (generate-access-token client user2 scope true)]

           (core/find-access-token (:access_token token1)) => (instance-of Token)
           (core/find-access-token (:access_token token2)) => (instance-of Token)

           ;; when
           (core/revoke-client-tokens (:id client) (:login user1))

           ;; then
           (core/find-access-token (:access_token token1)) => nil
           (core/find-access-token (:access_token token2)) =not=> nil
           (count (core/find-refresh-tokens (:id client))) => 1)))

 ?store :in-memory :redis :sql)

(tabular
 (fact "Regenerated tokens override and revoke old ones."
       (with-stores ?store

         ;; given
         (let [user   (create-test-user "")
               client (create-test-client scope redirect-uri)
               access-token  (generate-access-token client user scope true)
               refresh-token (first (core/find-refresh-tokens (:id client)))]

           ;; when
           (let [new-token (core/regenerate-tokens (:id client)
                                                   (:login user)
                                                   scope)]
             ;; then
             (= (:access_token new-token) (:access_token access-token)) => false
             (= (:refresh_token new-token) (:refresh_token access-token)) => false

             (core/find-access-token (:access_token access-token)) => nil
             (count (core/find-refresh-tokens (:id client))) => 1))))

 ?store :in-memory :redis :sql)

(fact "Tokens cannot be generated for disabled user or client."
       (with-stores :in-memory

         ;; given
         (let [user   (create-test-user "")
               client (create-test-client scope redirect-uri)]

           ;; and
           (core/disable-client (:id client))

           ;; when
           (let [access-token (core/regenerate-tokens (:id client)
                                                      (:login user)
                                                      scope)]

             ;; then
             access-token => (instance-of HttpError)
             (first (core/find-refresh-tokens (:id client))) => nil))))

(fact "Tokens with expires-at date in the past are considered as expired ones."
      (with-stores :in-memory

        ;; given
        (let [user   (create-test-user "")
              client (create-test-client scope redirect-uri)]

          ;; when
          (helpers/expired?
           (create-token :access client user scope -10))) => true))

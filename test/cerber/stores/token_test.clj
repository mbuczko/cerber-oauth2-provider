(ns cerber.stores.token-test
  (:require [midje.sweet :refer :all]
            [cerber.store :refer [now-plus-seconds expired?]]
            [cerber.oauth2.common :refer :all]
            [cerber.stores.token :refer :all]
            [clojure.tools.logging :as log])
  (:import  [cerber.stores.token Token]))

(def token-scope "photo:read")

(fact "Newly created token is returned as with user/client ids and secret filled in."
      (with-token-store (create-token-store :in-memory)
        (let [token (create-token client-foo user-nioh token-scope)]
          token => (instance-of Token)
          token => (has-secret :secret)
          token => (contains {:client-id (:id client-foo)
                              :user-id (:id user-nioh)
                              :login "nioh"
                              :scope token-scope}))))

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Token found in a store is returned with user/client ids and secret filled in."
         (with-token-store (create-token-store ?store)
           (purge-tokens)
           (let [token (create-token client-foo user-nioh token-scope)
                 found (find-access-token (:secret token))]
             found => (instance-of Token)
             found => (has-secret :secret)
             found => (contains {:client-id (:id client-foo)
                                 :user-id (:id user-nioh)
                                 :login "nioh"
                                 :scope token-scope})))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Revoked token is not returned from store."
         (with-token-store (create-token-store ?store)
           (purge-tokens)
           (let [token  (create-token client-bar user-nioh token-scope)
                 secret (:secret token)]
             (find-access-token secret) => (instance-of Token)
             (revoke-token token)
             (find-access-token secret) => nil))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Refreshing re-generates access/refresh tokens and revokes old ones from store."
         (with-token-store (create-token-store ?store)
           (purge-tokens)
           (let [access-token (generate-access-token client-foo user-nioh token-scope)
                 refresh-token (find-refresh-token (:refresh_token access-token))]

             (let [new-token (refresh-access-token refresh-token)]
               (= (:access_token new-token) (:access_token access-token)) => false
               (= (:refresh_token new-token) (:refresh_token access-token)) => false
               (find-access-token (:access_token access-token)) => truthy
               (find-refresh-token (:secret refresh-token)) => falsey)))))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Tokens with ttl shorter or longer than valid-for are marked respectively as expired or valid ones."
       (against-background (default-valid-for) => ?offset)
       (with-token-store (create-token-store :in-memory)
         (expired?
          (create-token client-foo user-nioh token-scope)) => ?expired))

 ?offset ?expired
 -10     true
 10      false)

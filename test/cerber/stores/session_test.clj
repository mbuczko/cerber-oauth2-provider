(ns cerber.stores.session-test
  (:require [cerber.test-utils :refer [instance-of has-secret with-stores]]
            [cerber.stores.session :refer :all]
            [midje.sweet :refer :all]
            [cerber.oauth2.settings :as settings])
  (:import cerber.stores.session.Session))

(def session-content {:sample "value"})

(fact "Created session has a session content."
      (with-stores :in-memory

        ;; given
        (let [session (create-session session-content)]

          ;; then
          session => (instance-of Session)
          session => (contains {:content session-content}))))

(tabular
 (fact "Sessions are stored in a correct model."
       (with-stores ?store

         ;; given
         (let [created (create-session session-content)
               session (find-session (:sid created))]

           ;; then
           session => (instance-of Session)
           session => (has-secret :sid)
           session => (contains {:content session-content})

           (:expires-at session) =not=> nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Expired sessions are removed from store."
       (with-stores ?store

         ;; given
         (let [session (create-session session-content -1)]

           ;; then
           (find-session (:sid session)) => nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Extended session has expires-at updated."
       (with-stores ?store

         ;; given
         (let [created (create-session session-content 1)
               expires (:expires-at created)

               ;; when
               session (find-session (:sid (extend-session created)))]

           ;; then
           (compare (:expires-at session) expires) => 1)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Existing sessions can be updated with new content."
       (with-stores ?store

         ;; given
         (let [created (create-session session-content)

               ;; when
               updated (update-session (assoc created :content {:sample "updated"}))
               session (find-session (:sid updated))]

           ;; then
           (-> updated :content :sample) => "updated"
           (-> session :content :sample) => "updated"

           (= (:sid updated) (:sid session)) => true
           (= (:sid created) (:sid session)) => true)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Non-existent sessions cannot be updated."
       (with-stores ?store
         (update-session (map->Session {:sid "123" :content session-content})) => nil))

 ?store :in-memory :sql :redis)

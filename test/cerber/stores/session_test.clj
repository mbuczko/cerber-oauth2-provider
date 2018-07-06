(ns cerber.stores.session-test
  (:require [cerber.test-utils :refer [instance-of has-secret with-stores]]
            [cerber.stores.session :refer :all]
            [midje.sweet :refer :all])
  (:import cerber.stores.session.Session))

(def session-content {:sample "value"})

(fact "Newly created session is returned with session content and random id filled in."
      (with-stores :in-memory

        ;; given
        (let [session (create-session session-content)]

          ;; then
          session => (instance-of Session)
          session => (has-secret :sid)
          session => (contains {:content session-content}))))

(tabular
 (fact "Session found in a store is returned session content and random id filled in."
       (with-stores ?store

         ;; given
         (let [initial (create-session session-content)
               session (find-session (:sid initial))]

           ;; then
           session => (instance-of Session)
           session => (has-secret :sid)
           session => (contains {:content session-content}))))

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
         (let [initial (create-session session-content 1)
               expires (:expires-at initial)

               ;; when
               session (find-session (:sid (extend-session initial)))]

           ;; then
           (compare (:expires-at session) expires) => 1)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Existing sessions can be updated with new content."
       (with-stores ?store

         ;; given
         (let [initial (create-session session-content)

               ;; when
               updated (update-session (assoc initial :content {:sample "updated"}))
               session (find-session (:sid updated))]

           ;; then
           (-> updated :content :sample) => "updated"
           (-> session :content :sample) => "updated"
           (= (:sid updated) (:sid session)) => true
           (= (:sid initial) (:sid session)) => true)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Non-existent sessions cannot be updated."
       (with-stores ?store
         (update-session (map->Session {:sid "123" :content session-content})) => nil))

 ?store :in-memory :sql :redis)

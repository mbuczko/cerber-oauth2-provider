(ns cerber.stores.session-test
  (:require [cerber.test-utils :refer [instance-of has-secret with-storage]]
            [cerber.stores.session :as s]
            [midje.sweet :refer [fact tabular => =not=> contains just truthy]])
  (:import cerber.stores.session.Session))

(def session-content {:sample "value"})

(fact "Created session has a session content."
      (with-storage :in-memory

        ;; given
        (let [session (s/create-session session-content)]

          ;; then
          session => (instance-of Session)
          session => (contains {:content session-content}))))

(tabular
 (fact "Sessions are stored in a correct model."
       (with-storage ?storage

         ;; given
         (let [created (s/create-session session-content)
               session (s/find-session (:sid created))]

           ;; then
           session => (instance-of Session)
           session => (has-secret :sid)
           session => (contains {:content session-content})

           (:expires-at session) =not=> nil)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Expired sessions are removed from store."
       (with-storage ?storage

         ;; given
         (let [session (s/create-session session-content -1)]

           ;; then
           (s/find-session (:sid session)) => nil)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Extended session has expires-at updated."
       (with-storage ?storage

         ;; given
         (let [created (s/create-session session-content 1)
               expires (:expires-at created)

               ;; when
               session (s/find-session (:sid (s/extend-session created)))]

           ;; then
           (compare (:expires-at session) expires) => 1)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Existing sessions can be updated with new content."
       (with-storage ?storage

         ;; given
         (let [created (s/create-session session-content)

               ;; when
               updated (s/update-session (assoc created :content {:sample "updated"}))
               session (s/find-session (:sid updated))]

           ;; then
           (-> updated :content :sample) => "updated"
           (-> session :content :sample) => "updated"

           (= (:sid updated) (:sid session)) => true
           (= (:sid created) (:sid session)) => true)))

 ?storage :in-memory :sql :redis)

(tabular
 (fact "Non-existent sessions cannot be updated."
       (with-storage ?storage
         (s/update-session (s/map->Session {:sid "123" :content session-content})) => nil))

 ?storage :in-memory :sql :redis)

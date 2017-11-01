(ns cerber.stores.session-test
  (:require [cerber.common-test :refer :all]
            [cerber.stores.session :refer :all]
            [midje.sweet :refer :all])
  (:import cerber.stores.session.Session))

(fact "Newly created session is returned with session content and random id filled in."
      (with-session-store (create-session-store :in-memory)

        ;; given
        (let [content {:sample "value"}
              session (create-session content)]

          ;; then
          session => (instance-of Session)
          session => (has-secret :sid)
          session => (contains {:content content}))))

(tabular
 (fact "Session found in a store is returned session content and random id filled in."
       (with-session-store (create-session-store ?store)
         (purge-sessions)

         ;; given
         (let [content {:sample "value"}
               initial (create-session content)
               session (find-session (:sid initial))]

           ;; then
           session => (instance-of Session)
           session => (has-secret :sid)
           session => (contains {:content content}))))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Expired sessions are removed from store."
       (with-session-store (create-session-store ?store)
         (purge-sessions)

         ;; given
         (let [session (create-session {:sample "value"} -1)]

           ;; then
           (find-session (:sid session)) => nil)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Extended session has expires-at updated."
       (with-session-store (create-session-store ?store)
         (purge-sessions)

         ;; given
         (let [initial (create-session {:sample "value"} 1)
               expires (:expires-at initial)

               ;; when
               session (find-session (:sid (extend-session initial)))]

           ;; then
           (compare (:expires-at session) expires) => 1)))

 ?store :in-memory :sql :redis)

(tabular
 (fact "Existing sessions can be updated with new content."
       (with-session-store (create-session-store ?store)
         (purge-sessions)

         ;; given
         (let [initial (create-session {:sample "value"})

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
       (with-session-store (create-session-store ?store)
         (purge-sessions)
         (update-session (map->Session {:sid "123" :content {:sample "value"}})) => nil))

 ?store :in-memory :sql :redis)

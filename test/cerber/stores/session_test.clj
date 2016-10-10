(ns cerber.stores.session-test
  (:require [cerber.common :refer :all]
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
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
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
             session => (contains {:content content})))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Expired sessions are removed from store."
         (with-session-store (create-session-store ?store)
           (purge-sessions)

           ;; given
           (let [session (create-session {:sample "value"} {:ttl -1})]

             ;; then
             (find-session (:sid session)) => nil))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Extended session has expires-at updated."
         (with-session-store (create-session-store ?store)
           (purge-sessions)

           ;; given
           (let [initial (create-session {:sample "value"})
                 expires (:expires-at initial)

                 ;; when
                 session (extend-session initial)]

             ;; then
             (compare (:expires-at session) expires) => 1))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
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
             (= (:sid initial) (:sid session)) => true))))

 ?store :in-memory :sql :redis)

(tabular
 (with-state-changes [(before :contents (.start redis))
                      (after  :contents (.stop redis))]
   (fact "Non-existent sessions cannot be updated."
         (with-session-store (create-session-store ?store)
           (purge-sessions)
           (update-session (map->Session {:sid "123" :content {:sample "value"}})) => nil)))

 ?store :in-memory :sql :redis)

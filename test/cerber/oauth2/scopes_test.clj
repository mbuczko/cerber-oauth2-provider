(ns cerber.oauth2.scopes-test
  (:require [cerber.oauth2.scopes :refer :all]
            [midje.sweet :refer :all]))

(tabular
 (fact "Normalizes scopes by removing duplicates and overlaps"
       (normalize-scopes ?scope) => ?normalized)

 ?scope                                    ?normalized
 #{"photos:read" "photos:write" "photos"}  #{"photos"}
 #{"photos:read" "photos:write"}           #{"photos:read" "photos:write"}
 #{"photos:read" "user:read" "user"}       #{"photos:read" "user"}
 #{}                                       #{}
 nil                                       #{})

(tabular
 (fact "Verifies if all scopes are predefined as allowed ones"
       (let [allowed-scopes #{"photos:read"  {:description "grants read-only priviledges to photos"}
                              "photos:write" {:description "grants modification priviledges to photos"}}]
         (allowed-scopes? ?scopes allowed-scopes)) => ?expected)

 ?scopes                          ?expected
 #{"photos:read"}                 true
 #{"photos:read" "photos:write"}  true
 #{"user:read"}                   false
 #{"photos:read" "user:read"}     false
 #{}                              true
 nil                              true)

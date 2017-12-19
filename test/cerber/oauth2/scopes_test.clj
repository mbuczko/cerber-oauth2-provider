(ns cerber.oauth2.scopes-test
  (:require [cerber.oauth2.scopes :refer :all]
            [cerber.stores.client :refer :all]
            [midje.sweet :refer :all]))

(tabular
 (fact "Normalizes scope string by removing duplicates and overlaps"
       (normalize-scope ?scope) => ?normalized)

 ?scope                             ?normalized
 "photos:read photos:write photos"  #{"photos"}
 "photos:read photos:write"         #{"photos:read" "photos:write"}
 "photos:read user:read user"       #{"photos:read" "user"}
 ""                                 #{}
 nil                                #{})

(tabular
 (fact "Valid scopes should be included in client definition."
       (let [client (create-client "dummy" ["http://localhost"] [] ["photos:read" "photos:write"] false)]
         (scopes-valid? client ?scopes) => ?expected))

 ?scopes                          ?expected
 #{"photos:read"}                 true
 #{"photos:read" "photos:write"}  true
 #{"user:read"}                   false
 #{"photos:read" "user:read"}     false
 #{}                              true
 nil                              true)

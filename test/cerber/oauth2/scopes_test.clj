(ns cerber.oauth2.scopes-test
  (:require [cerber.oauth2.core :as core]
            [cerber.oauth2.scopes :refer [normalize-scope]]
            [cerber.stores.client :refer [scopes-valid?]]
            [cerber.test-utils :refer [with-stores]]
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
       (with-stores :in-memory
         (let [client (core/create-client "dummy" ["http://localhost"] nil ["photos:read" "photos:write"] true false)]
           (scopes-valid? client ?scopes) => ?expected)))

 ?scopes                          ?expected
 #{"photos:read"}                 true
 #{"photos:read" "photos:write"}  true
 #{"user:read"}                   false
 #{"photos:read" "user:read"}     false
 #{}                              true
 nil                              true)

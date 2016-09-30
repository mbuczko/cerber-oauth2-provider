(ns cerber.middleware
  (:require [cerber.stores.session
             :refer
             [find-session revoke-session create-session update-session]]
            [ring.middleware.session.store :refer [SessionStore]]
            [clojure.tools.logging :as log]))

(deftype CustomStore []
  SessionStore
  (read-session [_ key]
    (log/info "read-session" key)
    (:content (find-session key {:extend-by nil})))
  (write-session [_ key data]
    (log/info "write-session" key data)
    (:sid
     (if key
       (when-let [session (find-session key)]
         (update-session (assoc session :content data)))
       (create-session data))))
  (delete-session [_ key]
    (log/info "delete-session" key)
    (revoke-session (find-session key))
    nil))

(defn session-store []
  (CustomStore.))

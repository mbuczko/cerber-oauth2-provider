(ns cerber.middleware
  (:require [cerber.stores.session
             :refer
             [create-session
              extend-session
              find-session
              revoke-session
              update-session]]
            [clojure.tools.logging :as log]
            [ring.middleware.session.store :refer [SessionStore]]))

(deftype CustomStore []
  SessionStore
  (read-session [_ key]
    (log/info "read-session" key)
    (when-let [session (find-session key)]
      (:content (extend-session session))))
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

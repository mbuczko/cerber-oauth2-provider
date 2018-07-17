(ns cerber.oauth2.core
  (:require [cerber.stores
             [user :as user]
             [token :as token]
             [client :as client]
             [session :as session]
             [authcode :as authcode]]
            [cerber.oauth2.settings :as settings]))

;; stores

(defn create-session-store
  "Initializes empty session store of given type - :in-memory, :sql or :redis one.
  Redis-based session store expects redis connection spec passed in a `config` parameter
  whereas SQL-based one requires an initialized database connection."

  [type config]
  (session/init-store type config))

(defn create-authcode-store
  "Initializes empty authcode store of given type - :in-memory, :sql or :redis one.
  Redis-based authcode store expects redis connection spec passed in a `config` parameter
  whereas SQL-based one requires an initialized database connection."

  [type config]
  (authcode/init-store type config))

(defn create-token-store
  "Initializes empty token store of given type - :in-memory, :sql or :redis one.
  Redis-based token store expects redis connection spec passed in a `config` parameter
  whereas SQL-based one requires an initialized database connection."

  [type config]
  (token/init-store type config))

(defn create-user-store
  "Initializes empty user store of given type - :in-memory, :sql or :redis one.
  Redis-based user store expects redis connection spec passed in a `config` parameter
  whereas SQL-based one requires an initialized database connection."

  [type config]
  (user/init-store type config))

(defn create-client-store
  "Initializes empty client store of given type - :in-memory, :sql or :redis one.
  Redis-based client store expects redis connection spec passed in a `config` parameter
  whereas SQL-based one requires an initialized database connection."

  [type config]
  (client/init-store type config))

;; clients

(defn find-client
  "Looks up for client with given identifier."

  [client-id]
  (client/find-client client-id))

(defn create-client
  "Creates new OAuth client.

    `info`      : a non-validated info string (typically client's app name or URL to client's homepage)
    `redirects` : a validated vector of approved redirect-uris.
                  redirect-uri provided with token request should match one of these entries.
    `grants`    : an optional vector of allowed grants: authorization_code, token, password or client_credentials; all grants allowed if set to nil
    `scopes`    : an optional vector of OAuth scopes that client may request an access to
    `enabled?`  : should client be automatically enabled?
    `approved?` : should client be auto-approved?

  Example:

      (c/create-client \"http://defunkt.pl\"
                       [\"http://defunkt.pl/callback\"]
                       [\"authorization_code\" \"password\"]
                       [\"photo:read\" \"photo:list\"]
                       true
                       false)"

  [info redirects grants scopes enabled? approved?]
  (client/create-client info redirects grants scopes enabled? approved?))

(defn delete-client
  "Removes client from store along with all its access- and refresh-tokens."

  [client-id]
  (when-let [client (find-client client-id)]
    (= 1 (client/revoke-client client))))

(defn disable-client
  "Disables client.

  Revokes all client's tokens and prevents from gaining new ones.
  When disabled, client is no longer able to request permissions to any resource."

  [client-id]
  (when-let [client (find-client client-id)]
    (client/disable-client client)))

(defn enable-client
  "Enables client.

  When enabled, client is able to request access to user's resource and (when accepted)
  get corresponding access-token in response."

  [client-id]
  (when-let [client (find-client client-id)]
    (client/enable-client client)))

;; users

(defn find-user
  "Looks up for a user with given login."

  [login]
  (user/find-user login))

(defn create-user
  "Creates new user with all the details like login, descriptive name, email and user's password.

  Example:

      (c/create-user {:login \"foobar\"
                      :name  \"Foo Bar\"
                      :email \"foo@bar.bazz\"
                      :roles #{\"user/admin\"}
                      :permissions #{\"photos:read\"}
                      :enabled? true}
                     \"secret\")"

  [{:keys [login name email roles permissions enabled?] :or {enabled? true}} password]
  (user/create-user {:login login
                     :name  name
                     :email email
                     :roles roles
                     :permissions permissions
                     :enabled? enabled?}
                    password))

(defn delete-user
  "Removes user from store."

  [login]
  (when-let [user (find-user login)]
    (= 1 (user/revoke-user user))))

(defn disable-user
  "Disables user.

  Disabled user is no longer able to authenticate and all access tokens created
  based on his grants become immediately invalid."

  [login]
  (when-let [user (find-user login)]
    (and (user/disable-user user) user)))

(defn enable-user
  "Enables user.

  Enabled user is able to authenticate and approve or deny access to
  resources requested by OAuth clients."

  [login]
  (when-let [user (find-user login)]
    (and (user/enable-user user) user)))


;; tokens

(defn find-access-token
  "Returns access-token bound to given secret."

  [secret]
  (token/find-access-token secret))

(defn revoke-access-token
  "Revokes single access-token."

  [secret]
  (token/revoke-access-token secret))

(defn find-refresh-tokens
  "Returns list of refresh tokens generated for given client (and optional user)."

  ([client-id]
   (find-refresh-tokens client-id nil))
  ([client-id login]
   (token/find-by-pattern ["refresh" nil client-id login])))

(defn revoke-client-tokens
  "Revokes all refresh-tokens bound with given client (and optional user)."
  ([client-id]
   (revoke-client-tokens client-id nil))
  ([client-id login]
   (when-let [client (find-client client-id)]
     (token/revoke-client-tokens client {:login login}))))

(defn regenerate-tokens
  "Generates both access- and refresh-tokens for given client-user pair.
  Revokes and overrides existing tokens, if any exist."

  [client-id login scope]
  (let [client (find-client client-id)
        user   (find-user login)]

    (when (and client user)
      (token/generate-access-token client user scope true))))

;; settings

(defn set-realm
  "Sets up a global OAuth2 realm. Returns newly set value."

  [realm]
  (settings/realm realm))

(defn set-authentication-url
  "Sets up a location that browser should redirect to in order
  to authenticate a user. Returns newly set value."

  [auth-url]
  (settings/authentication-url auth-url))

(defn set-landing-url
  "Sets up a landing URL that browser should redirect to after
  successful authentication. Returns newly set value."

  [landing-url]
  (settings/landing-url landing-url))

(defn set-token-valid-for
  "Sets up a token time-to-live (TTL) which essentially says
  how long OAuth2 tokens are valid. Returns newly set value."

  [valid-for]
  (settings/token-valid-for valid-for))

(defn set-authcode-valid-for
  "Sets up an auth-code time-to-live (TTL) which essentially says
  how long OAuth2 authcodes are valid. Returns newly set value."

  [valid-for]
  (settings/authcode-valid-for valid-for))

(defn set-session-valid-for
  "Sets up a session time-to-live (TTL) which essentially says
  how long OAuth2 sessions are valid. Returns newly set value."

  [valid-for]
  (settings/session-valid-for valid-for))

(defn update-settings
  "Bulk update of OAuth2 global settings with provided `settings` map."

  [settings]
  (settings/update-settings settings))

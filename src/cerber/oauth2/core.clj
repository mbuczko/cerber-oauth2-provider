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
  Redis-based session store expects redis connection spec passed in a `config` parameter."

  [type config]
  (session/init-store type config))

(defn create-authcode-store
  [type config]
  (authcode/init-store type config))

(defn create-token-store
  [type config]
  (token/init-store type config))

(defn create-user-store
  [type config]
  (user/init-store type config))

(defn create-client-store
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
    `approved?` : decides whether client should be auto-approved or not. Set to false by default.

  Example:

      (c/create-client \"http://defunkt.pl\"
                       [\"http://defunkt.pl/callback\"]
                       [\"authorization_code\" \"password\"]
                       [\"photo:read\" \"photo:list\"]
                       true)"

  [info redirects & [grants scopes approved?]]
  (client/create-client info redirects grants scopes approved?))

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
  "Creates new user with given login, descriptive name, user's email, password (stored as hash), roles and permissions.

    `roles`       : set of user's roles
    `permissions` : set of user's permissions
    `enabled?`    : decides whether user should be enabled or not. Set to true by default.

  Example:

      (c/create-user \"foobar\"
                     \"Foo Bar\"
                     \"foo@bar.bazz\"
                     \"secret\"
                     #{\"user/admin\"}
                     #{\"photos:read\"}
                     true)"

  [login name email password roles permissions enabled?]
  (user/create-user (user/map->User {:login login
                                     :name  name
                                     :email email
                                     :enabled? enabled?})
                    password
                    roles
                    permissions))

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

(defn find-tokens-by-client
  "Returns list of \"access\" or \"refresh\" tokens generated for given client."

  [client-id token-type]
  (token/find-by-pattern [client-id token-type nil]))

(defn find-tokens-by-user
  "Returns list of \"access\" or \"refresh\" tokens generated for clients operating on behalf of given user."

  [login token-type]
  (token/find-by-pattern [nil token-type nil login]))

(defn revoke-access-token
  "Revokes single access-token."

  [secret]
  (when-let [token (token/find-access-token secret)]
    (token/revoke-access-token token)))

(defn revoke-tokens
  "Revokes all access- and refresh-tokens bound with given client (and optional user)."

  ([client-id]
   (revoke-tokens client-id nil))
  ([client-id login]
   (when-let [client (find-client client-id)]
     (token/revoke-client-tokens client login))))

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

# Cerber - OAuth2 Provider

[![Clojars Project](https://img.shields.io/clojars/v/cerber/cerber-oauth2-provider.svg)](https://clojars.org/cerber/cerber-oauth2-provider)

[Architecture][arch] | [Configuration][conf] | [Usage][use] | [API][api] | [Middlewares][middlewares] | [Development][dev] | [Changelog][log]

This is a clojurey implementation of [RFC 6749 - The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749). Currently covers all scenarios described by spec:

* [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* [Implict Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

Tokens expiration and [refreshing](https://tools.ietf.org/html/rfc6749#section-6) are all in the box as well.

## Architecture

This implementation assumes Authorization Server and Resource Server having same source of knowledge about issued tokens and sessions.
Servers might be horizontally scaled but still need to be connected to the same underlaying database (redis or sql-based). This is also why in-memory storage should be used for development only. It simply does not scale (at least not with current implementation).

All _NOT RECOMMENDED_ points from specification have been purposely omitted for security reasons. Bearer tokens and client credentials should be passed in HTTP headers. All other ways (like query param or form fields) are ignored and will result in HTTP 401 (Unauthorized) or HTTP 403 (Forbidden) errors.

_(todo)_ introduce JWT tokens

### Users and clients

Cerber has its own abstraction of [User](./src/cerber/stores/user.clj) ([resource owner](https://tools.ietf.org/html/rfc6749#section-1.1)) and [Client](./src/cerber/stores/client.clj) (application which requests on behalf of User). Instances of both can be easily created with Cerber's API.

### Stores

_Store_ is a base abstraction of storage which, through protocol, exposes simple API to read and write entities (user, client, session, token or authorization code) that all the logic operates on. Cerber stands on a shoulders of 5 stores:

* users - keeps users details (along with encoded password)
* clients - keeps OAuth clients data (identifiers, secrets, allowed redirect URIs and so on)
* sessions - keeps http session data transmitted back and forth via [ring session](https://github.com/ring-clojure/ring/wiki/Sessions)
* tokens -  generated access- and refresh tokens
* authcodes - codes to be exchanged for tokens

As for now, each store implements following 3 types:

* `:in-memory` - a store keeping its data straight in `atom`. Ideal for development mode and tests.
* `:redis` - a proxy to Redis. Recommended for production mode.
* `:sql` - a proxy to relational database (eg. MySQL or PostgreSQL). Recommended for production mode.

To keep maximal flexibility each store can be configured separately, eg. typical configuration might use `:sql` store for users and clients and `:redis` one for sessions / tokens / authcodes.

When speaking of configuration...

## Configuration

`cerber.oauth2.core` namespace is a central place which exposes all the functions required to initialize stores, users, clients and tinker with global options like realm or token/authcode/session life-times. Stores might seem to be a bit tricky to configure as they depend on underlaying storage and thus may expect additional parameters. To configure session store as redis based one, following expression should make it happen:

``` clojure
(require '[cerber.core :as core])
(core/create-session-store :redis {:spec {:host "localhost"
                                          :port 6380}})
```

and this is how to configure SQL-based store which requires database connection passed in as a parameter:

``` clojure
(require '[cerber.core :as core])
(require '[conman.core :as conman])

(defonce db-conn
  (and (Class/forName "org.postgresql.Driver")
       (conman/connect! {:init-size  1
                         :min-idle   1
                         :max-idle   4
                         :max-active 32
                         :jdbc-url "jdbc:postgresql://localhost:5432/template1?user=postgres"})))
                         
(core/create-session-store :sql db-conn)
```

Initialization and tear-down process can be easily handed over to glorious [mount](https://github.com/tolitius/mount):

``` clojure
(require '[mount.core :refer [defstate]])

(defstate client-store
  :start (core/create-client-store :sql db-conn)
  :stop  (close! client-store))

(defstate user-store
  :start (core/create-user-store :sql db-conn)
  :stop  (close! user-store))

   ...and so on...
```

### Authorization Grant Types

Grant types allowed:

* `authorization_code` for [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* `token` for [Implict Code Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* `password` for [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* `client_credentials` for [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

### Scopes

Scopes are the OAuth way to explicitly manage the power associated with an access token. In nutshell, a scope says what type of access OAuth2 client may need to particular resource.

Cerber defines scopes as a set of unique strings like `user`, `photo:read` or `profile:write` which may be structurized in kind of hierarchy. For example scopes may be defined as a following: `#{"photo:read" "photo:write"}` which (when permission is granted) allows _reading_ and _writing_ to imaginary photo resoure. A `photo` scope itself is assumed to be a parent of `photo:read` and `photo:write` and implicitly includes both scopes.

In practice, scopes are auto-simplified, so when client asks for permission to `photo` and `photo:read` scopes, it's being simplified to `photo` only.

Note, it's perfectly valid to have an empty set of scopes as they are optional in OAuth2 spec.

### Roles and permissions

Although User model contains `roles` field it is not interpreted in any way. It is simply returned for further processing, eg. by custom middleware.

Please take a look at [cerber-roles](https://github.com/mbuczko/cerber-roles) to make use of roles in more meaningful way.

### Forms

To complete some of OAuth2-flow actions like web based authentication or asking user for approval, Cerber picks up following templates to render corresponding HTML pages:

 * [templates/cerber/login.html](./resources/templates/cerber/login.html) - used to render authentication form.
 * [templates/cerber/authorize.html](./resources/templates/cerber/authorize.html) - used to render an user's approval/rejection form to grant (or not) certain permissions.

Both templates are provided by this library with a very spartan styling, just to expose the most important things inside and should be replaced with own customized ones.

## Usage

Cerber OAuth2 provider defines 7 [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) that should be bound to specific routes. It's not done automagically. Some people love [compojure](https://github.com/weavejester/compojure) some love [bidi](https://github.com/juxt/bidi) so Cerber leaves the decision in developer's hands.

Anyway, this is how bindings would look like with compojure:

``` clojure
(require '[cerber.handlers :as handlers])

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler)
  (GET  "/logout"    [] handlers/logout-handler))
```

Having OAuth paths set up, next step is to configure routes to protected resources (assuming here a user's details as such a one):

``` clojure
(require '[cerber.oauth2.context :as ctx])

(defroutes authorized-routes
  (GET "/user/info" [] (fn [req] {:status 200
                                  :body (::ctx/user req)})))
```
Almost there. One missing part not mentioned yet is authorization and the way how token is validated.

All the magic happens inside `wrap-authorized` middleware which examines both request Cookie (for session identifier) and `Authorization` header (for a token issued by Authorization Server). Once token is found, requestor receives set of privileges it was asking for and request is delegated down into handlers stack. Otherwise 401 Unauthorized is returned.

``` clojure
(require '[org.httpkit.server :as web]
          [cerber.handlers :refer [wrap-authorized]]
          [compojure.core :refer [routes wrap-routes]
          [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
          [ring.middleware.format :refer [wrap-restful-format]]])

(def api-routes
  (routes oauth-routes
          (-> authorized-routes
              (wrap-routes wrap-restful-format :formats [:json-kw])
              (wrap-routes wrap-authorized)))

;; final handler passed to HTTP server (HTTP-Kit here)
(web/run-server (wrap-defaults api-routes api-defaults) {:host "localhost" :port 8080}})
```

## API

API functions are all grouped in `cerber.oauth2.core` namespace based on what entity they deal with.

### stores

`(create-user-store [type config])`

`(create-client-store [type config])`

`(create-session-store [type config])`

`(create-authcode-store [type config])`

`(create-token-store [type config])`

Functions to initialize empty store of given type - :in-memory, :sql or :redis one. Redis-based store expects redis connection spec
passed in a `config` parameter whereas SQL-based one requires an initialized database connection.

### clients

`(create-client [info redirects grants scopes enabled? approved?])`

`(create-client [info redirects grants scopes enabled? approved? id secret])`

Used to create new OAuth client, where:
- `info` is a non-validated info string (typically client's app name or URL to client's homepage)
- `redirects` is a validated vector of approved redirect-uris. Note that for security reasons redirect-uri provided with token request should match one of these entries.
- `grants` is vector of allowed grants: "authorization\_code", "token", "password" or "client\_credentials". if nil - all grants are allowed.
- `scopes` is vector of OAuth scopes that client may request an access to
- `enabled?` decides whether client should be auto-enabled or not. It's false by default which means client is not able to request for tokens.
- `approved?` decides whether client should be auto-approved or not. It's false by default which means that client needs user's approval when requesting access to protected resource.
- `id` - optional client-id, auto-generated if none provided.
- `secret` - optional client-secret, auto-generated if none provided.

Example:

```clojure
(require '[cerber.oauth2.core :as c])

(c/create-client "http://defunkt.pl"
                 ["http://defunkt.pl/callback"]
                 ["authorization_code" "password"]
                 ["photo:read" "photo:list"]
                 true
                 false)
```

Each generated client has its own random client-id and a secret which both are used in OAuth flow.
Important thing is to keep the secret codes _really_ secret! Both client-id and secret authorize
client instance and it might be harmful to let attacker know what's your client's secret code is.

`(find-client [client-id])`

Looks up for client with given identifier.

`(delete-client [client])`

Removes client from store. Note that together with client all its access- and refresh-tokens are revoked as well.

`(disable-client [client-id])`

`(enable-client [client-id])`

Disables or enables client with given identifier. Disabled client is no longer able to receive access/refresh-tokens nor operate on behalf of user in any other way.

### users

`(create-user [details password])`

Creates new user with given map of details:

- `:login` is a user's login identifier
- `:name` is a user's description (like full name)
- `:email` is a user's email
- `:roles` set of optional roles
- `:enabled?` indicates whether user should be enabled. User is enabled by default unless `enabled?` states otherwise.

`(find-user [login])`

Looks up for a user with given login.

`(delete-user [login])`

Removes from store user with given login.

`(disable-user [login])`

`(enable-user [login])`

Disables or enables user with given given login. Disabled user is no longer able to authenticate and all authorization attempts fail immediately.

`(init-users [users])`
`(init-clients [clients])`

Initializes users- and clients-store with predefined collection of users/clients:

```clojure
(require '[cerber.oauth2.core :as c])

(c/init-users [{:login "admin"
                :email "admin@bar.com"
                :name "Admin"
                :enabled? true
                :password "secret"
                :roles #{"user/admin"}}
               {:login "foo"
                :email "foo@bar.com"
                :name "Foo Bar"
                :enabled? true
                :password "pass"
                :roles #{"user/all"}}])
```


### tokens

`(find-access-token [secret])`

Returns an access token bound to given secret.

`(revoke-access-token [secret])`

Revokes given access-token.

`(find-refresh-tokens [client-id])`

`(find-refresh-tokens [client-id login])`

Returns collection of refresh-tokens for given client (and user optionally).

`(revoke-client-tokens [client-id])`

`(revoke-client-tokens [client-id login])`

Revokes all access- and refresh-tokens bound with given client (and user optionally).

`(regenerate-tokens [client-id login scope])`

Refreshes tokens for given client-user pair. Revokes and overrides existing tokens, if any exist.

### global options

`(set-token-valid-for! valid-for)`

Sets up a token time-to-live (TTL) which essentially says how long OAuth2 tokens are valid.

`(set-authcode-valid-for! valid-for)`

Sets up an authcode time-to-live (TTL) which essentially says how long authcodes are valid.

`(set-session-valid-for! valid-for)`

Sets up a session time-to-live (TTL) which essentially says how long sessions are valid.

`(set-landing-url! url)`

Sets up a location that browser should redirect to in order to authenticate a user.

`(set-realm! realm)`

Sets up a realm presented in WWW-Authenticate header in case of 401/403 http error codes.

`(set-authentication-url! url)`

Sets up an OAuth2 authentication URL ("/login" by default).

`(set-unauthorized-url! url)`

Sets up location where browser redirects in case of `HTTP 401 Unauthorized` ("/login" by default).

### errors

Any errors returned in a response body are formed according to specification as following json:

``` json
{
  "error": "error code",
  "error_description": "human error description",
  "state": "optional state"
}
```

## Middlewares

Cerber exposes 2 middlewares in `cerber.handlers` namespace:

`wrap-authorized`

This one, based on cookie or bearer token conveyed in a request, sets up a context where a subject (authorized user) and OAuth2 client information is stored for a request time-life.
Unauthorized requests result in `HTTP 401 Unauthorized` (in case of invalid token) or redirection to login page (in case of cookie based request).

`wrap-maybe-authorized`

Same as `wrap-authorized` but does no redirection or `HTTP 401 Unauthorized` responses in case of unauthorized requests. In this case a request context is simply not created and no user/client information is available.

## Development

Underlaying [midje](https://github.com/marick/Midje) testing framework has been configured to watch for changes and run corresponding tests after each change:

``` shell
$ boot tests
```

This library has also built-in [standalone testing server](./src/cerber/oauth2/standalone/server.clj) available in `cerber.oauth2.standalone.server` namespace. All it needs to start up is initialized with mount-based restartable system:

``` clojure
(require '[cerber.oauth2.standalone.system :as system])

;; start server
(system/go)

;; stops server
(system/stop)

;; restart server
(system/reset)
```

Any ideas or bugfixes? PRs nicely welcomed. Be sure that your changes pass all the tests or simply add your own test suites if none covers your code yet.

## Changelog

- `v1.1.0` : `wrap-authorized` handler no longer wraps response in `wrap-restful-format` middleware, so response is not returned as json now. from now on, it' up to developer what format response will be transformed to.

[arch]: #architecture
[conf]: #configuration
[use]: #usage
[api]: #api
[middlewares]: #middlewares
[dev]: #development
[log]: #changelog

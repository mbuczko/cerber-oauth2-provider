# Cerber - OAuth2 Provider

[![Clojars Project](https://img.shields.io/clojars/v/cerber/cerber-oauth2-provider.svg)](https://clojars.org/cerber/cerber-oauth2-provider)

[Architecture][arch] | [Usage][use] | [API][api] | [FAQ][faq] | [Development][dev]

This is a clojurey implementation of [RFC 6749 - The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749).
Currently covers all scenarios described by spec:

* [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* [Implict Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

Tokens expiration and [refreshing](https://tools.ietf.org/html/rfc6749#section-6) are all in the box as well.

## Architecture

This implementation assumes Authorization Server and Resource Server having same source of knowledge about issued tokens and sessions.
Servers might be horizontally scaled but still need to be connected to the same underlaying database (redis or sql-based).
This is also why in-memory storage should be used for development only. It simply does not scale (at least not with current implementation).

All _NOT RECOMMENDED_ points from specification have been purposely omitted for security reasons. Bearer tokens and client credentials should be
passed in HTTP headers. All other ways (like query param or form fields) are ignored and will result in HTTP 401 (Unauthorized) or HTTP 403
(Forbidden) errors.

_(todo)_ introduce JWT tokens

### Stores

_Store_ is a base abstraction of storage which, through protocol, exposes simple API to read and write entities (user, client, session, token or
authorization code) that all the logic operates on. Cerber stands on a shoulders of 5 stores:

* users - keeps users details (along with encoded password)
* clients - keeps OAuth clients data (identifiers, secrets, allowed redirect URIs and so on)
* sessions - keeps http session data transmitted back and forth via [ring session](https://github.com/ring-clojure/ring/wiki/Sessions)
* tokens -  generated access- and refresh tokens
* authcodes - codes to be exchanged for tokens

As for now, each store implements following types:

* `in-memory` - a store keeping its data straight in `atom`. Ideal for development mode and tests.
* `redis` - a proxy to Redis. Recommended for production mode.
* `sql` - a proxy to relational database (eg. MySQL or PostgreSQL). Recommended for production mode.

To keep maximal flexibility each store can be configured separately, eg. typical configuration might use `sql` store for users and clients and `redis`
one for sessions / tokens / authcodes.

When speaking of configuration...

### Configuration

Cerber uses glorious [mount](https://github.com/tolitius/mount) to set up everything it needs to operate. Instead of creating stores by hand it's enough
to describe them in EDN based configuration:

``` clojure
{:authcodes   {:store :sql :valid-for 180}
 :sessions    {:store :sql :valid-for 180}
 :tokens      {:store :sql :valid-for 180}
 :users       {:store :sql}
 :clients     {:store :sql}
 :landing-url "/"
 :realm       "http://defunkt.pl"
 :endpoints   {:authentication "/login"
               :client-approve "/approve"
               :client-refuse  "/refuse"}}
 :redis-spec  {:spec {:host "localhost" :port 6379}}
 :jdbc-pool   {:init-size  1
               :min-idle   1
               :max-idle   4
               :max-active 32
               :driver-class "org.h2.Driver"
               :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/migrations/h2/schema.sql'"}}
```

Words of explanation:

 * `authcodes` auth-codes store definition, requires an auth-code life-time option (:valid-for) in seconds.
 * `sessions` sessions store definition, requires a session life-time option (:valid-for) in seconds.
 * `tokens` tokens store definition, requires a token life-time option (:valid-for) in seconds.
 * `users` users store definition.
 * `clients` oauth2 clients store definition.
 * `redis-spec` (optional) is a redis connection specification (look at [carmine](https://github.com/ptaoussanis/carmine) for more info) for redis-based stores.
 * `jdbc-pool` (optional) is a sql database pool specification (look at [conman](https://github.com/luminus-framework/conman) for more info) for sql-based stores.
 * `endpoints` (optional) should reflect cerber's routes to authentication and access approve/refuse endpoints.
 * `realm` (required) is a realm presented in WWW-Authenticate header in case of 401/403 http error codes

#### Users and clients

Cerber has its own abstraction of User ([resource owner](https://tools.ietf.org/html/rfc6749#section-1.1)) and Client (application which
requests on behalf of User). Instances of both can be easily created using cerber's API.

To initialize users and/or clients as a part of environment, the first thing is to decide what storage should hold these entities.
If it's an :in-memory one, following entries should be placed into `cerber.edn` config file:

``` clojure
{:users   {:store :in-memory}
 :clients {:store :in-memory}}
```

Now, initialization boils down to call of `create-user` or `create-client` functions:

``` clojure
(require '[cerber.core :as c]')

(c/create-user "foobar" "Foo Bar" "foo@bar.bazz" "secret" #{"user/admin"} #{"photos:read"} true)

(c/create-client "http://defunkt.pl"
                 ["http://defunkt.pl/callback"]
                 ["authorization_code" "password"]
                 ["photo:read" "photo:list"]
                 true
                 "KEJ57AVGDWJA4YSEUBX3H3M2RBW53WLA"
                 "BOQUIIPBU5LDJ5BBZMZQYZZK2KTLHLBS")
```

#### Authorization Grant Types

Grant types allowed:

* `authorization_code` for [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* `token` for [Implict Code Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* `password` for [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* `client_credentials` for [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

#### Scopes

Client scopes are configured as a vector of unique strings like `"user"`, `"photo:read"` or `"profile:write"` which may be structurized in kind of hierarchy.
For example one can define scopes as `#{"photo" "photo:read" "photo:write"}` which grants _read_ and _write_ permission to imaginary photo resoure and
a _photo_ permission which is a parent of _photo:read_ and _photo:write_ and implicitly includes both permissions.

Cerber also normalizes scope requests, so when client asks for `#{"photo" "photo:read"}` scopes, it's been simplified to `#{"photo"}` only.

Note, it's perfectly valid to have an empty set of scopes as they are optional in OAuth2 spec.

### Environments

When Cerber's system boots up, it tries first to load default EDN confguration is located in `cerber.edn`. By default it sets up all the stores to in-memory
and configures required endpoints:

``` clojure
{:landing-url "http://localhost"
 :realm       "http://localhost"
 :authcodes   {:store :in-memory :valid-for 600}
 :sessions    {:store :in-memory :valid-for 600}
 :tokens      {:store :in-memory :valid-for 600}
 :users       {:store :in-memory}
 :clients     {:store :in-memory}
 :endpoints   {:authentication "/login"
               :client-approve "/approve"
               :client-refuse  "/refuse"}}
```
Configuration can be easily extended or replaced in 3 ways:

 - including custom `cerber.edn` in project resources
 - including environment-specific configuration file `cerber-ENV.edn` where ENV may be one of following:
   * value of `ENV` environmental variable
   * value of `ENV` JVM system property
   * `local` if no `ENV` environmental variable or JVM system property were found
 - including both `cerber.edn` and environment specific `cerber-ENV.edn`. In this case both resources will be merged together (environment-specific entries have
 higher priority in case of conflicts).

### Forms

To complete some of OAuth2-flow actions, like web based authentication or approval dialog Cerber makes use of following templates to render HTML pages:

 * [templates/cerber/login.html](./resources/templates/cerber/login.html) - used to render authentication form.
 * [templates/cerber/authorize.html](./resources/templates/cerber/authorize.html) - used to render user a form where user is asked to grant a permission.

Both templates are provided by this library with a very spartan styling, just to expose the most important things inside and should be replaced with own customized ones.

## Usage

Cerber OAuth2 provider defines 6 [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) that should be bound to specific routes. It's not done automagically.
Some people love [compojure](https://github.com/weavejester/compojure) some love [bidi](https://github.com/juxt/bidi) so Cerber leaves the decision in developer's hands.

Anyway, this is how bindings would look like with compojure:

``` clojure
(require '[cerber.handlers :as handlers])

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))
```

To recall, any change in default /login, /approve or /refuse paths should be reflected in corresponding `endpoints` part of configuration.

Having OAuth Authentication Server paths set up, next step is to configure restricted resources:

``` clojure
(require '[cerber.oauth2.context :as ctx])

(defroutes restricted-routes
  (GET "/user/info" [] (fn [req] {:status 200
                                  :body (::ctx/user req)})))
```
Almost there. One missing part not mentioned yet is authorization and the way how token is validated.

All this magic happens inside `handlers/wrap-authorized` handler which scans `Authorization` header for a token issued by Authorization Server.
Once token is found, requestor receives set of privileges it was asking for and request is delegated down into handlers stack. Otherwise 401 Unauthorized is returned.

``` clojure
(require '[org.httpkit.server :as web]
          [compojure.core :refer [routes wrap-routes]
          [ring.middleware.defaults :refer [api-defaults wrap-defaults]]])

(def api-routes
  (routes oauth-routes
          (wrap-routes restricted-routes handlers/wrap-authorized))

;; final handler passed to HTTP server
(def app-handler (wrap-defaults api-routes api-defaults))

;; for HTTP-Kit
(web/run-server app-handler {:host "localhost" :port 8080}})
```

### Grand finale

Having all the bits and pieces configured, it's time to run _mount_ machinery:

``` clojure
(require '[cerber.oauth2.standalone.system :as system])
(system/go)
```

This simply starts the Cerber system by mounting all stores and populates them with configured users and clients (if any defined).


## API

API functions are all grouped in `cerber.oauth2.core` namespace and allow to manipulate with clients, users and tokens at higher level.

Full documentation can be found [here](http://api.defunkt.pl/cerber/api/cerber.oauth2.core.html).

### errors

Any errors returned in a response body are formed according to specification as following json:

``` json
{
  "error": "error code",
  "error_description": "human error description",
  "state": "optional state"
}
```

or added to the _error_ query param in case of callback requests.

Callback requests (redirects) are one of the crucial concepts of OAuth flow thus it's extremally important to have redirect URIs verified. There are several way to validate redirect URI,
this implementation however goes the simplest way and does _exact match_ which means that URI provided by client in a request MUST be exactly the same as one of URIs bound to the client during registration.

## Development

Cerber can be comfortably developed in [TDD](https://en.wikipedia.org/wiki/Test-driven_development) mode. Underlaying [midje](https://github.com/marick/Midje) testing framework has been configured to watch for changes and run automatically as a boot task:

``` shell
$ boot tests
```

Important thing is that tests go through all possible store types (including sql and redis) which means a running redis instance is expected.
For sql-based stores an HSQL database is used so no other running SQL databases are required.

As usual, PRs nicely welcomed :) Be sure first that your changes pass the tests or simply add your own tests if you found no ones covering your code yet.

[arch]: #architecture
[use]: #usage
[api]: #api
[faq]: #faq
[dev]: #development

# cerber / OAuth2 provider

[![Clojars Project](https://img.shields.io/clojars/v/cerber/cerber-oauth2-provider.svg)](https://clojars.org/cerber/cerber-oauth2-provider)

This is a work-in-progress of Clojurey implementation of [RFC 6749 - The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749).

Currently covers all scenarios described by spec:

* [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* [Implict Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

Tokens expiration and [refreshing](https://tools.ietf.org/html/rfc6749#section-6) are all in the box as well.

To make all of this happen, Cerber stands on a shoulders of Stores.

## Stores

Store is an abstraction of storage keeping information vital for Cerber. There are 5 stores introduced:

* users - keeps users details (along with encoded password)
* clients - keeps OAuth clients data (identifiers, secrets, allowed redirect URIs and so on)
* sessions - keeps http session data transmitted back and forth via [ring session](https://github.com/ring-clojure/ring/wiki/Sessions)
* tokens -  generated access- and refresh tokens
* authcodes - codes to be exchanged for tokens

All stores may use one of following implementations:

* ```in-memory``` - ideal for development mode and tests
* ```redis``` - recommended for production mode
* ```sql``` - any JDBC compliant SQL database (eg. MySQL or PostgreSQL)

To keep maximal flexibility, each store can use different store implementation. It's definitely recommended to use ```in-memory``` stores for development process and persistent ones for production.
Typical configuration might use ```sql``` for users and clients and ```redis``` for sessions / tokens / authcodes.

When speaking of configuration...

## Configuration

Cerber uses glorious [mount](https://github.com/tolitius/mount) to set up everything it needs to operate. Instead of creating stores by hand it's pretty enough to adjust edn-based configuration file
specific for each environment (local / test / prod):

``` clojure
{:authcodes   {:store :sql :valid-for 180}
 :sessions    {:store :sql :valid-for 180}
 :tokens      {:store :sql :valid-for 180}
 :users       {:store :sql}
 :clients     {:store :sql}
 :scopes      #{}
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

 * ```authcodes``` auth-codes store definition, requires an auth-code life-time option (:valid-for) in seconds.
 * ```sessions``` sessions store definition, requires a session life-time option (:valid-for) in seconds.
 * ```tokens``` tokens store definition, requires a token life-time option (:valid-for) in seconds.
 * ```users``` users store definition.
 * ```clients``` oauth2 clients store definition.
 * ```redis-spec``` (optional) is a redis connection specification (look at [carmine](https://github.com/ptaoussanis/carmine) for more info) for redis-based stores.
 * ```jdbc-pool``` (optional) is a sql database pool specification (look at [conman](https://github.com/luminus-framework/conman) for more info) for sql-based stores.
 * ```endpoints``` (optional) should reflect cerber's routes to authentication and access approve/refuse endpoints.
 * ```realm``` (required) is a realm presented in WWW-Authenticate header in case of 401/403 http error codes
 * ```scopes``` (required) available set of [scopes](https://www.oauth.com/oauth2-servers/scope/defining-scopes/) for oauth2 clients.

How are scopes defined?

Scopes are configured as a set of unique strings like ```"user"```, ```"photos:read"``` or ```"profile:write"``` which may be structurized in kind of hierarchy.
For example one can define scopes as ```#{"photos" "photos:read" "photos:write"}``` which gives any OAuth2 client (if configured so) a privilege to ask for
_read_ and _write_ permission to imaginary photos resoure and a _photos_ permission which includes both _photos:read_ and _photos:write_. In other words, _photos_ is
a scope which groups all other scopes beginning with _photos:_.

Cerber also normalizes scope requests, so when client asks for ```#{"photos" "photos:read"}``` scopes, it's been simplified to ```#{"photos"}``` only.

Also, it's perfectly valid to have an empty set of scopes as they are optional in OAuth2 spec. On the other hand if any scopes have been defined,
all client requests are validated against configuration.

### Configuration files

When Cerber's system boots up, first it tries to find and load default edn-based confgurations which are simply resources available within a classpath.
Specifically, system searches for ```cerber.edn``` (described above) and merges it with optional ```cerber-ENV.edn```. Latter one is used to
override default options (eg. stores definitions) based on environment controlled by ```ENV``` variable. When no environmental variable ENV is set,
it immediately defaults to ```local```, so ```cerber-local.edn``` is loaded (if found) and merged with ```cerber.edn```.

### HTML resources

To complete some of OAuth2-flow actions, like web based authentication or access-grant dialog, Cerber tries to load HTML templates, fill them in
and present to the end-user. In similar way how it goes with configuration, Cerber looks for 2 HTML templates:

 * [forms/login.html](./config/templates/forms/login.html) - used to render authentication form.
 * [forms/authorize.html](./config/templates/forms/authorize.html) - used to render user a form where user is asked to grant a permission.

Having all the bits and pieces adjusted, it's time to run _mount_ machinery:

``` clojure
(require '[mount.core :as mount])
(mount/start)
```

## Architecture

This implementation assumes Authorization Server and Resource Server having same source of knowledge about issued tokens and sessions.
Servers might be horizontally scaled but still need to be connected to the same underlaying database (redis or sql-based). This is also why in-memory
storage should be used for development only. It simply does not scale (at least not with current implementation).

_(todo)_ introduce JWT tokens

## Implementation

All _NOT RECOMMENDED_ points from specification have been purposely omitted for security reasons. Bearer tokens and client credentials should be passed in HTTP
headers. All other ways (like query param or form fields) are ignored and will result in HTTP 401 (Unauthorized) or HTTP 403 (Forbidden) errors.

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

## Usage

Cerber OAuth2 provider defines 6 [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) that should be bound to specific routes. It's not done automagically. Some people love [compojure](https://github.com/weavejester/compojure) some love [bidi](https://github.com/juxt/bidi) so Cerber leaves the decision in developer's hands.

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

To recall, any change in default /login, /approve or /refuse paths should be reflected in corresponding ```endpoints``` part of configuration.

Having OAuth Authentication Server paths set up, next step is to configure restricted resources:

``` clojure
(defroutes restricted-routes
  (GET "/user/info" [] user-info-handler))
```

Let's define ```user-info-handler``` to return user's details:

``` clojure
(require '[cerber.oauth2.context :as ctx])

(defn user-info-handler [req]
  {:status 200
   :body (::ctx/user req)})
```

Almost there. To verify tokens as an OAuth Resource Server Cerber bases on a single ring wrapper - ```handlers/wrap-authorized```.
It's responsible for looking for a token in HTTP ```Authorization``` header and checking whether token matches one issued by Authorization Server.

``` clojure
(require '[compojure.core :refer [routes wrap-routes]
          [ring.middleware.defaults :refer [api-defaults wrap-defaults]]])

(def app
  (wrap-defaults
   (routes
    oauth-routes
    (wrap-routes restricted-routes handlers/wrap-authorized))
   api-defaults))
```

## API

API functions are all grouped in ```cerber.oauth2.core``` namespace and allow to manipulate with clients and tokens at higher level.

### clients

```(create-client [info redirects & [grants scopes approved?]])```

used to create new OAuth client, where:
- info is a non-validated info string (typically client's app name or URL to client's homepage)
- redirects is a validated vector of approved redirect-uris. Note that for security reasons redirect-uri provided with token request should match one of these entries.
- grants is an optional vector of allowed grants: "authorization_code", "token", "password" or "client_credentials". if nil - all grants are allowed.
- scopes is an optional vector of OAuth scopes that client may request an access to
- approved? is an optional parameter deciding whether client should be auto-approved or not. It's false by default which means that client needs user's approval when requesting access to protected resource.

Example:

```clojure
    (require '[cerber.oauth2.core :as c])

    (c/create-client "http://defunkt.pl"
                     ["http://defunkt.pl/callback"]
                     ["authorization_code" "password"]
                     ["photos:read" "photos:list"]
                     true)
```

Each generated client has its own random client-id and a secret which both are used in OAuth flow.
Important thing is to keep the secret codes _really_ secret! Both client-id and secret authorize
client instance and it might be harmful to let attacker know what's your client's secret code is.

```(find-client [client-id])```

Looks up for client with given identifier.

```(delete-client [client])```

Removes client from store. Note that together with client all its access- and refresh-tokens are revoked as well.

### tokens

```(find-tokens-by-client [client])```

Returns list of non-expirable refresh-tokens generated for given client.

```(find-tokens-by-user [user])```

Returns list of non-expirable refresh-tokens generated for clients operating on behalf of given user.

```(revoke-tokens [client])```

```(revoke-tokens [client login])```

Revokes all access- and refresh-tokens bound with given client (and optional user's login).


## FAQ

#### I've chosen SQL engine for some of my stores. How to determine what the database schema is?

Cerber uses SQL migrations (handled by [flyway](https://flywaydb.org/)) to incrementally apply changes on database schema. All migrations live [here](https://github.com/mbuczko/cerber-oauth2-provider/tree/master/resources/db/migrations). You may either apply them by hand or use task which will migrate schema changes for supported SQL databases:

``` shell

$ boot -d cerber/cerber-oauth2-provider migrate -j <jdbc-url>

$ boot -d cerber/cerber-oauth2-provider migrate -j "jdbc:mysql://localhost:3306/template1?user=root&password=secret"

```

### What SQL databases are supported?

Currently MySQL and Postgres are supported out of the box and recognized based on jdbc-url.

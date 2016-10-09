# cerber / OAuth2 provider

This is a work-in-progress of Clojurey implementation of [RFC 6749 - The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749).

Currently covers all scenarios described by spec:

* [Authorization Code Grant](https://tools.ietf.org/html/rfc6749#section-4.1)
* [Implict Grant](https://tools.ietf.org/html/rfc6749#section-4.2)
* [Resource Owner Password Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.3)
* [Client Credentials Grant](https://tools.ietf.org/html/rfc6749#section-4.4)

Tokens expiration and [refreshing](https://tools.ietf.org/html/rfc6749#section-6) are all in the box as well.

To make all of this happen, Cerber sits on a shoulders of Stores.

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

Now, when it comes to configuration...

## Configuration

Cerber uses glorious [mount](https://github.com/tolitius/mount) to set up everything it needs to operate. Instead of creating stores by hand it's pretty enough to adjust simple edn-based configuration file
specific for each environment (local / test / prod):

``` clojure
{:server {:host "localhost" :port 8090}
 :cerber {:redis-spec {:spec {:host "localhost" :port 6379}}
          :jdbc-pool  {:init-size  1
                       :min-idle   1
                       :max-idle   4
                       :max-active 32
                       :driver-class "org.h2.Driver"
                       :jdbc-url "jdbc:h2:mem:testdb;MODE=MySQL;INIT=RUNSCRIPT FROM 'classpath:/db/schema.sql'"}
          :endpoints  {:authentication "/login"
                       :authorization  "/authorize"}
          :authcodes  {:store :sql :valid-for 180}
          :sessions   {:store :sql :valid-for 180}
          :tokens     {:store :sql :valid-for 180}
          :users      {:store :sql}
          :clients    {:store :sql}
          :realm      "http://defunkt.pl"
          :landing-url "/"}}
```

Words of explanation:

```redis-spec``` (optional) is a redis connection specification (look at [carmine](https://github.com/ptaoussanis/carmine) for more info)

```jdbc-pool``` (optional) is a sql database pool specification (look at [conman](https://github.com/luminus-framework/conman) for more info)

```endpoints``` (optional) any change in default OAuth authentication/authorization URLs need to be reflected here

```realm``` (required) is a realm presented in WWW-Authenticate header in case of 401/403 http error codes

authcodes / sessions / tokens / users and clients are required stores configurations. Note that authcodes, sessions and tokens NEED to have life-time (in seconds) configured.

Having all the bits and pieces adjusted, throw configuration file into your classpath (usually _resources_ folder) with descriptive name as ```name.edn``` (eg. cerber.edn) or ```name-environment.edn``` (eg. cerber-local.edn).
Configurations are hierarchical which means that both files will be read and merged together (with precedence of -environment.edn entries).

Now, when all that boring stuff is done, time to run _mount_ machinery:

``` clojure
(require '[mount.core :as m])

(m/start-with-args {:env "local" :base-name "cerber"})
```

## Architecture

This implementation assumes Authorization Server and Resource Server having same source of knowledge about issued tokens and sessions.
Servers might be horizontally scaled but still need to be connected to the same underlaying database (redis or sql-based). This is also why in-memory
storage should be used for development only. It simply does not scale (at least not with current implementation).

_(todo)_ introduce JWT tokens

## Implementation

All _NOT RECOMMENDED_ points from specification have been purposely omitted for security reasons. Bearer tokens and client credentials should be passed in HTTP
headers. All other ways (like query param or form fields) are ignored and will result in HTTP 401 (Unauthorized) or HTTP 403 (Forbidden) errors.

Any errors returned by this implementation should be formed according to specification:

``` json
{
  "error": "error code",
  "error_description": "human error description",
  "state": "optional state"
}
```

## Usage

Cerber OAuth2 provider defines 5 [ring handlers](https://github.com/ring-clojure/ring/wiki/Concepts) that should be bound to specific routes. It's not done automagically. Some people love [compojure](https://github.com/weavejester/compojure) some love [bidi](https://github.com/juxt/bidi) so Cerber leaves the decision in developer's hands.

Anyway, this is how bindings would look like with compojure:

``` clojure
(require '[cerber.handlers :as handlers])

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/authorize" [] handlers/authorization-approve-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))
```

To recall, anytime /login or /authorize paths change it should be reflected in ```endpoints```  part of configuration.

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

Almost there. To verify tokens as an OAuth Resource Server Cerber bases on a single ring wrapper - ```handlers/wrap-token-auth```.
It's responsible for looking for a token in HTTP ```Authorization``` header and checking whether token matches one issued by Authorization Server.

``` clojure
(require '[compojure.core :refer [routes wrap-routes]
          [ring.middleware.defaults :refer [api-defaults wrap-defaults]]])

(def app
  (wrap-defaults
   (routes
    oauth-routes
    (wrap-routes restricted-routes handlers/wrap-token-auth))
   api-defaults))
```

## API

API functions are all grouped in ```cerber.oauth2.core``` namespace and allow to manipulate with clients and tokens at higher level.

### clients

```(create-client [homepage redirects scopes grants authorities approved?])```

used to create new OAuth client, where:
- homepage is a non-validated info string (typically an URL to client's homepage)
- redirects is a validated vector of approved redirect-uris. Note that for security reasons redirect-uri provided with token request should match one of these entries.
- scopes is an optional vector of OAuth scopes that client may request an access to
- authorities is an optional vector of authorities that client may operate with
- approved? is an optional parameter deciding whether client should be auto-approved or not. It's false by default which means that client needs user's approval when requesting access to protected resource.

Example:

```clojure
    (require '[cerber.oauth2.core :as c])

    (c/create-client "http://defunkt.pl"
                     ["http://defunkt.pl/callback"]
                     ["photos:read" "photos:list"]
                     ["moderator"]
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

Cerber uses SQL migrations (handled by [flyway](https://flywaydb.org/)) to incrementally apply changes on database schema. If you want to see how do they look like, or want to apply them by hand, they all live [here](https://github.com/mbuczko/cerber-oauth2-provider/tree/master/resources/db/migrations).

To apply them in a bit easier way without checking out sources, you may want to use boot directly:

``` shell
boot -d cerber-oauth2-provider flyway -m -j "jdbc:mysql://localhost:3306/template1?user=root&password=alamakota"
```

where ```-j``` is a jdbc URL to database, ```-m``` just says to apply pending migrations. You may also use (with caution!) ```-c``` to clean db. When no switch (aside from -j) was used, information about applied migrations will be shown:

``` shell
~/w/c/cerber-oauth2-provider $ boot -d cerber-oauth2-provider -j "jdbc:mysql://localhost:3306/template1?user=root&password=alamakota"
+----------------+-------------+---------------------+---------+
| Version        | Description | Installed on        | State   |
+----------------+-------------+---------------------+---------+
| 20161007012907 | init schema | 2016-10-09 23:25:32 | Success |
+----------------+-------------+---------------------+---------+
```

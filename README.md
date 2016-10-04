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

Store is an abstract of storage keeping information vital for Cerber. There are 4 stores implemented:

* users - keeps users details (along with encoded password)
* clients - keeps OAuth clients data (identifiers, secrets, allowed redirect URIs and so on)
* session store - keeps http session data transmitted back and forth via [ring session](https://github.com/ring-clojure/ring/wiki/Sessions)
* tokens store -  generated access- and refresh tokens
* authcodes store - codes to be exchanged for tokens

Store abstract has currenlty following implementations:
* ```in-memory``` - ideal for development mode and tests
* ```redis``` - recommended for production mode
* ```sql``` - any JDBC compliant SQL database (eg. MySQL or PostgreSQL)

To keep maximal flexibility, each store can use different store implementation. It's recommended to use in-memory stores for development process and persistent ones for production.
Typical configuration might use ```sql``` for users and clients and ```redis``` for sessions/tokens/authcodes.

## API

API functions are all grouped in ```cerber.oauth2.core``` namespace and allow to manipulate with clients and tokens at higher level.

### clients

```create-client [homepage redirects scopes grants authorities approved?]```

used to create new OAuth client, where:
- homepage is a non-validated info string (typically an URL to client's homepage)
- redirects is an vector of valid redirect-uris. Note that according to spec, redirect-uri provided with token request should match one of entries listed in ```redirects```
- scopes is an optional vector of OAuth scopes that client may request an access to.
- authorities is an optional vector of authorities that client may operate with.
- approved? is an optional parameter deciding whether client should be auto-approved or not. It's false by default (client needs user's approval).

Example:

    (create-client "http://defunkt.pl" ["http://defunkt.pl/callback"] ["photos:read" "photos:list"] ["moderator"] true)

### tokens

(tbd)

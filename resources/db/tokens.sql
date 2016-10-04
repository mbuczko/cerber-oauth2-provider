-- :name find-tokens-by-secret :? :*
-- :doc Returns tokens found by secret
select * from tokens where client_id=:client-id and secret=:secret and tag=:tag

-- :name find-tokens-by-login-and-client :? :*
-- :doc Returns tokens found by client id and login
select * from tokens where client_id=:client-id and login=:login and tag=:tag

-- :name find-tokens-by-login :? :*
-- :doc Returns tokens found by login
select * from tokens where login=:login and tag=:tag

-- :name insert-token :! :1
-- :doc Inserts new token
insert into tokens (client_id, user_id, secret, scope, login, tag, created_at, expires_at) values (:client-id, :user-id, :secret, :scope, :login, :tag, :created-at, :expires-at)

-- :name delete-token-by-secret :! :1
-- :doc Deletes token by client and secret
delete from tokens where client_id=:client-id and secret=:secret

-- :name delete-tokens-by-login :! :1
-- :doc Deletes access token
delete from tokens where client_id=:client-id and login=:login

-- :name delete-tokens-by-client :! :1
-- :doc Deletes access token
delete from tokens where client_id=:client-id

-- :name clear-tokens :! :1
-- :doc Purges tokens table
delete from tokens

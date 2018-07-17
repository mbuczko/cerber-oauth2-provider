-- :name find-tokens-by-secret :? :*
-- :doc Returns tokens found by secret
select * from tokens where secret=:secret and ttype=:ttype

-- :name find-tokens-by-client :? :*
-- :doc Returns tokens found by client id
select * from tokens where client_id=:client-id and ttype=:ttype

-- :name insert-token :! :1
-- :doc Inserts new token
insert into tokens (client_id, user_id, secret, scope, login, ttype, created_at, expires_at) values (:client-id, :user-id, :secret, :scope, :login, :ttype, :created-at, :expires-at)

-- :name delete-token-by-secret :! :1
-- :doc Deletes token by secret
delete from tokens where secret=:secret

-- :name delete-tokens-by-login :! :1
-- :doc Deletes access token
delete from tokens where client_id=:client-id and login=:login and ttype=:ttype

-- :name delete-tokens-by-client :! :1
-- :doc Deletes access token
delete from tokens where client_id=:client-id and ttype=:ttype

-- :name clear-tokens :! :1
-- :doc Purges tokens table
delete from tokens

-- :name clear-expired-tokens :! :1
-- :doc Purges tokens table from expired token
delete from tokens where expires_at < :date

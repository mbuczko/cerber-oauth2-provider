-- :name find-token-by-secret :? :*
-- :doc Returns token for given secret
select * from tokens where secret=:secret and is_refresh=:is-refresh

-- :name find-token-by-details :? :*
-- :doc Returns token for given user and client ids
select * from tokens where user_id=:user-id and client_id=:client-id and is_refresh=:is-refresh

-- :name insert-token :! :1
-- :doc Inserts new token
insert into tokens (client_id, user_id, secret, scope, login, is_refresh, created_at, expires_at) values (:client-id, :user-id, :secret, :scope, :login, :is-refresh, :created-at, :expires-at)

-- :name delete-token-by-secret :! :1
-- :doc Deletes token for particular secret
delete from tokens where secret=:secret

-- :name delete-token-by-details :! :1
-- :doc Deletes token for given user and client ids
delete from tokens where user_id=:user-id and client_id=:client-id

-- :name clear-tokens :! :1
-- :doc Purges tokens table
delete from tokens

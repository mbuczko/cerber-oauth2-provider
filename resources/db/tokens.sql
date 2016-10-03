-- :name find-access-token :? :*
-- :doc Returns access token
select * from tokens where client_id=:client-id and secret=:secret and refreshing is null

-- :name find-refresh-token-by-secret :? :*
-- :doc Returns refresh token
select * from tokens where client_id=:client-id and secret=:secret and refreshing is not null

-- :name find-refresh-token-by-client :? :*
-- :doc Returns refresh token
select * from tokens where client_id=:client-id and login=:login and refreshing is not null

-- :name find-refresh-token-by-login :? :*
-- :doc Returns refresh token
select * from tokens where login=:login and refreshing is not null

-- :name insert-token :! :1
-- :doc Inserts new token
insert into tokens (client_id, user_id, secret, scope, login, refreshing, created_at, expires_at) values (:client-id, :user-id, :secret, :scope, :login, :refreshing, :created-at, :expires-at)

-- :name delete-token :! :1
-- :doc Deletes access token
delete from tokens where client_id=:client-id and secret=:secret

-- :name clear-tokens :! :1
-- :doc Purges tokens table
delete from tokens

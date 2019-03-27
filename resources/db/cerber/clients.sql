-- :name find-client :? :1
-- :doc Returns client with given client identifier
select * from clients where id=:id

-- :name insert-client :! :1
-- :doc Inserts new client
insert into clients (id, secret, info, redirects, scopes, grants, approved, enabled, created_at, activated_at) values (:id, :secret, :info, :redirects, :scopes, :grants, :approved?, :enabled?, :created-at, :activated-at)

-- :name enable-client :! :1
-- :doc Enables client
update clients set enabled=true, activated_at=:activated-at where id=:id

-- :name disable-client :! :1
-- :doc Disables client
update clients set enabled=false, blocked_at=:blocked-at where id=:id

-- :name delete-client :! :1
-- :doc Deletes client with given identifier
delete from clients where id=:id

-- :name clear-clients :! :1
-- :doc Purges clients table
delete from clients;

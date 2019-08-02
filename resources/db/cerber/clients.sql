-- :name find-client :? :1
-- :doc Returns client with given client identifier
select id, secret, info, approved, scopes, grants, redirects, created_at, modified_at, blocked_at
  from clients
 where id = :id

-- :name insert-client :! :1
-- :doc Inserts new client
insert into clients (id, secret, info, redirects, scopes, grants, approved, created_at)
values (:id, :secret, :info, :redirects, :scopes, :grants, :approved?, :created-at)

-- :name enable-client :! :1
-- :doc Enables client
update clients set blocked_at = NULL where id = :id

-- :name update-client :! :1
-- :doc Disables client
update clients
   set info = :info, approved = :approved?, scopes = :scopes, grants = :grants, redirects = :redirects, modified_at = :modified-at, blocked_at = :blocked-at
 where id = :id

-- :name delete-client :! :1
-- :doc Deletes client with given identifier
delete from clients where id = :id

-- :name clear-clients :! :1
-- :doc Purges clients table
delete from clients;

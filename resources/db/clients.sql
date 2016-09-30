-- :name find-client :? :*
-- :doc Returns client with given client identifier
select * from clients where id=:id

-- :name insert-client :! :1
-- :doc Inserts new client
insert into clients (id, secret, homepage, redirects, scopes, grants, authorities) values (:id, :secret, :homepage, :redirects, :scopes, :grants, :authorities)

-- :name delete-client :! :1
-- :doc Deletes client with given identifier
delete from clients where id=:id

-- :name clear-clients :! :1
-- :doc Purges clients table
delete from clients;

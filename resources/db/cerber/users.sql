-- :name find-user :? :1
-- :doc Returns user with given login
select id, login, email, name, password, roles, created_at, modified_at, blocked_at
from users
where login = :login

-- :name insert-user :! :1
-- :doc Inserts new user
insert into users (id, login, email, name, password, roles, created_at)
values (:id, :login, :email, :name, :password, :roles, :created-at)

-- :name enable-user :! :1
-- :doc Enables user
update users set blocked_at = NULL where login = :login

-- :name disable-user :! :1
-- :doc Disables user
update users set blocked_at = :blocked-at where login = :login

-- :name delete-user :! :1
-- :doc Deletes user
delete from users where login = :login

-- :name clear-users :! :1
-- :doc Purges users table
delete from users;

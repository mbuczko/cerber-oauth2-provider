-- :name find-user :? :1
-- :doc Returns user with given login
select id, login, email, name, password, roles, created_at, modified_at, blocked_at
  from users
 where login = :login

-- :name insert-user :! :1
-- :doc Inserts new user
insert into users (id, login, email, name, password, roles, created_at)
values (:id, :login, :email, :name, :password, :roles, :created-at)

-- :name update-user :! :1
-- :doc Updates user data
update users
   set email = :email, name = :name, password = :password, roles = :roles, modified_at = :modified-at, blocked_at = :blocked-at
where login = :login

-- :name delete-user :! :1
-- :doc Deletes user
delete from users where login = :login

-- :name clear-users :! :1
-- :doc Purges users table
delete from users;

-- :name find-user :? :1
-- :doc Returns user with given login
select * from users where login=:login

-- :name insert-user :! :1
-- :doc Inserts new user
insert into users (id, login, email, name, password, roles, enabled, created_at, activated_at) values (:id, :login, :email, :name, :password, :roles, :enabled?, :created-at, :activated-at)

-- :name enable-user :! :1
-- :doc Enables user
update users set enabled=true, activated_at=:activated-at where login=:login

-- :name disable-user :! :1
-- :doc Disables user
update users set enabled=false, blocked_at=:blocked-at where login=:login

-- :name delete-user :! :1
-- :doc Deletes user
delete from users where login=:login

-- :name clear-users :! :1
-- :doc Purges users table
delete from users;

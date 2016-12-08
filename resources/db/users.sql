-- :name find-user :? :*
-- :doc Returns user with given login
select * from users where login=:login

-- :name insert-user :! :1
-- :doc Inserts new user
insert into users (id, login, email, name, password, authorities, enabled, created_at) values (:id, :login, :email, :name, :password, :authorities, :enabled, :created-at)

-- :name delete-user :! :1
-- :doc Deletes user
delete from users where login=:login

-- :name clear-users :! :1
-- :doc Purges users table
delete from users;

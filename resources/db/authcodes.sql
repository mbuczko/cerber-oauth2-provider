-- :name find-authcode :? :*
-- :doc Returns authcode bound with given code
select * from authcodes where code=:code

-- :name insert-authcode :! :1
-- :doc Inserts new authcode
insert into authcodes (code, redirect_uri, client_id, user_id, scope, login, expires_at, created_at) values (:code, :redirect-uri, :client-id, :user-id, :scope, :login, :expires-at, :created-at)

-- :name delete-authcode :! :1
-- :doc Deletes authcode bound with given code
delete from authcodes where code=:code

-- :name clear-authcodes :! :1
-- :doc Purges authcodes table
delete from authcodes;

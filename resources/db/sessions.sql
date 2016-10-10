-- :name find-session :? :*
-- :doc Returns session for given session id
select * from sessions where sid=:sid

-- :name insert-session :! :1
-- :doc Inserts new session
insert into sessions (sid, content, created_at, expires_at) values (:sid, :content, :created-at, :expires-at)

-- :name delete-session :! :1
-- :doc Deletes session for particular session id
delete from sessions where sid=:sid

-- :name clear-sessions :! :1
-- :doc Purges sessions table
delete from sessions

-- :name update-session :! :1
-- :doc Updates session entry
update sessions set content=:content,expires_at=:expires-at where sid=:sid

-- :name update-session-expiration :! :1
-- :doc Updates expiration date only
update sessions set expires_at=:expires-at where sid=:sid

-- :name clear-expired-sessions :! :1
-- :doc Purges tokens table from expired token
delete from sessions where expires_at < :date

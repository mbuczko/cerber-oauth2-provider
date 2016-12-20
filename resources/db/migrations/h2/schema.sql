drop table users if exists;
drop table tokens if exists;
drop table clients if exists;
drop table sessions if exists;
drop table authcodes if exists;

create table tokens (
  id int auto_increment primary key,
  tag varchar(10),
  client_id varchar(32) not null,
  user_id varchar(50),
  secret varchar(64) not null,
  scope varchar(255),
  login varchar(32),
  expires_at timestamp,
  created_at timestamp not null,
  UNIQUE KEY tokens_unique (secret)
);

create table users (
  id varchar(32) primary key,
  login varchar(32) not null,
  email varchar(128),
  name varchar(128),
  password varchar(255),
  roles varchar(1024),
  permissions varchar(1024),
  enabled bit not null default true,
  created_at datetime not null,
  activated_at datetime,
  blocked_at datetime,
  UNIQUE KEY users_login_unique (login)
);

create table sessions (
  sid varchar(36) primary key,
  content varbinary(2048),
  expires_at datetime not null,
  created_at datetime not null
);

create table authcodes (
  id int auto_increment primary key,
  client_id varchar(32) not null,
  login varchar(32) not null,
  code varchar(32) not null,
  scope varchar(255),
  redirect_uri varchar(255),
  expires_at datetime not null,
  created_at datetime not null,
  UNIQUE KEY authcodes_unique (code, redirect_uri)
);

create table clients (
  id varchar(32) primary key,
  secret varchar(32) not null,
  info varchar(255),
  approved bit not null default false,
  scopes varchar(1024),
  grants varchar(255),
  redirects varchar(512) not null,
  created_at datetime not null
);

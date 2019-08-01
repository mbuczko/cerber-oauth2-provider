drop table users if exists;
drop table tokens if exists;
drop table clients if exists;
drop table sessions if exists;
drop table authcodes if exists;

create table tokens (
  id int auto_increment primary key,
  ttype varchar(10),
  client_id varchar(32) not null,
  user_id varchar(50),
  secret varchar(64) not null,
  scope varchar(255),
  login varchar(32),
  expires_at timestamp,
  created_at timestamp not null,
  UNIQUE KEY tokens_unique (secret)
);

create index tokens_ttype_idx on tokens (ttype);
create index tokens_client_id_idx on tokens (client_id);
create index tokens_user_id_idx on tokens (user_id);
create index tokens_login_idx on tokens (login);

create table users (
  id varchar(32) primary key,
  login varchar(32) not null,
  email varchar(128),
  name varchar(128),
  password varchar(255),
  roles varchar(1024),
  created_at datetime not null,
  modified_at datetime,
  blocked_at datetime,
  UNIQUE KEY users_login_unique (login)
);

create index users_login_idx on users (login);

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

create index authcodes_client_id_idx on authcodes (client_id);
create index authcodes_login_idx on authcodes (login);
create index authcodes_code_idx on authcodes (code);

create table clients (
  id varchar(32) primary key,
  secret varchar(32) not null,
  info varchar(255),
  approved bit not null default false,
  scopes varchar(1024),
  grants varchar(255),
  redirects varchar(512) not null,
  created_at datetime not null,
  modified_at datetime,
  blocked_at datetime
);

create index clients_secret_idx on clients (secret);

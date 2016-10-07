-- migration to be applied

create table tokens (
  id int auto_increment primary key,
  tag varchar(10),
  client_id varchar(32) not null,
  user_id varchar(50),
  secret varchar(32) not null,
  scope varchar(255),
  login varchar(32) not null,
  expires_at datetime,
  created_at datetime not null,
  UNIQUE KEY tokens_unique (secret)
);

create table users (
  id int auto_increment primary key,
  login varchar(32) not null,
  email varchar(50),
  name varchar(128),
  password varchar(255) not null,
  authorities varchar(1024),
  enabled bit not null default true,
  created_at datetime not null,
  UNIQUE KEY users_login_unique (login),
  UNIQUE KEY users_id_unique (id)
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
  homepage varchar(255),
  approved bit not null default false,
  scopes varchar(1024),
  grants varchar(255),
  redirects varchar(512) not null,
  authorities varchar(1024),
  created_at datetime not null
);

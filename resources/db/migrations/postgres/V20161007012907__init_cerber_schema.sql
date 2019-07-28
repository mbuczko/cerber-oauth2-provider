-- migration to be applied

create table tokens (
  id serial primary key,
  ttype varchar(10),
  client_id varchar(32) not null,
  user_id varchar(50),
  secret varchar(64) not null UNIQUE,
  scope varchar(255),
  login varchar(32),
  expires_at timestamp,
  created_at timestamp not null
);

create index on tokens (ttype);
create index on tokens (client_id);
create index on tokens (user_id);
create index on tokens (login);

create table users (
  id varchar(32) primary key,
  login varchar(32) not null UNIQUE,
  email varchar(50),
  name varchar(128),
  password varchar(255),
  roles varchar(1024),
  enabled boolean not null default true,
  created_at timestamp not null,
  modified_at timestamp,
  activated_at timestamp,
  blocked_at timestamp
);

create index on users (login);

create table sessions (
  sid varchar(36) primary key,
  content bytea,
  expires_at timestamp not null,
  created_at timestamp not null
);

create table authcodes (
  id serial primary key,
  client_id varchar(32) not null,
  login varchar(32) not null,
  code varchar(32) not null,
  scope varchar(255),
  redirect_uri varchar(255),
  expires_at timestamp not null,
  created_at timestamp not null,
  UNIQUE (code, redirect_uri)
);

create index on authcodes (client_id);
create index on authcodes (login);
create index on authcodes (code);

create table clients (
  id varchar(32) primary key,
  secret varchar(32) not null,
  info varchar(255),
  approved boolean not null default false,
  enabled boolean not null default true,
  scopes varchar(1024),
  grants varchar(255),
  redirects varchar(512) not null,
  created_at timestamp not null,
  modified_at timestamp,
  activated_at timestamp,
  blocked_at timestamp
);

create index on clients (secret);

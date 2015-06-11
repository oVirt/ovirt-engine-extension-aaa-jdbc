CREATE SEQUENCE schema_version_seq;

CREATE TABLE schema_version (
  id integer DEFAULT nextval('schema_version_seq'::regclass) NOT NULL,
  version character varying(10) NOT NULL,
  script character varying(255) NOT NULL,
  checksum character varying(128),
  installed_by character varying(63),
  started_at timestamp without time zone DEFAULT now(),
  ended_at timestamp without time zone,
  state character varying(15) NOT NULL,
  current boolean NOT NULL,
  comment text DEFAULT ''::text,
  PRIMARY KEY(id)
);


CREATE TABLE settings(
  id SERIAL,
  uuid VARCHAR(512) UNIQUE NOT NULL,
  name VARCHAR(512) NOT NULL,
  description TEXT NOT NULL,
  value VARCHAR(1024) NOT NULL,
  PRIMARY KEY(id)
);

CREATE TABLE users(
  id SERIAL,
  uuid VARCHAR(512) UNIQUE NOT NULL,
  name VARCHAR(512) UNIQUE NOT NULL,
  password VARCHAR(1024) NOT NULL,
  password_valid_to TIMESTAMP NOT NULL,
  login_allowed VARCHAR(1024) NOT NULL,
  nopasswd INTEGER NOT NULL,
  disabled INTEGER NOT NULL,
  unlock_time TIMESTAMP NOT NULL,
  last_successful_login TIMESTAMP NOT NULL,
  last_unsuccessful_login TIMESTAMP NOT NULL,
  consecutive_failures INTEGER NOT NULL,
  valid_from TIMESTAMP NOT NULL,
  valid_to TIMESTAMP NOT NULL,
  PRIMARY KEY(id)
);

CREATE TABLE user_password_history(
  id SERIAL,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  password VARCHAR(1024) NOT NULL,
  changed TIMESTAMP NOT NULL,
  PRIMARY KEY(id)
);

CREATE TABLE user_attributes(
  id SERIAL,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(512) NOT NULL,
  value VARCHAR(1024) NOT NULL,
  PRIMARY KEY(id),
  UNIQUE (user_id,name)
);

CREATE TABLE failed_logins(
  id SERIAL,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  minute_start TIMESTAMP NOT NULL,
  count INTEGER NOT NULL,
  PRIMARY KEY(id),
  UNIQUE (user_id,minute_start)
);

CREATE INDEX failed_logins_index
ON failed_logins (minute_start);

CREATE TABLE groups(
  id SERIAL,
  name VARCHAR(512) UNIQUE NOT NULL,
  uuid VARCHAR(512) UNIQUE NOT NULL,
  PRIMARY KEY(id)
);

CREATE TABLE group_attributes(
  id SERIAL,
  group_id INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  name VARCHAR(512) NOT NULL,
  value VARCHAR(1024) NOT NULL,
  PRIMARY KEY(id),
  UNIQUE (group_id,name)
);

CREATE TABLE user_groups(
  id SERIAL,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  in_group_id INTEGER NOT NULL REFERENCES groups(id),
  PRIMARY KEY(id),
  UNIQUE(user_id, in_group_id)
);

CREATE TABLE group_groups(
  id SERIAL,
  group_id INTEGER NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  in_group_id INTEGER NOT NULL REFERENCES groups(id),
  PRIMARY KEY(id),
  UNIQUE(group_id,in_group_id)
);

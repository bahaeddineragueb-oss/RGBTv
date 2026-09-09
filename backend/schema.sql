-- RGBTv backend schema (PostgreSQL)
CREATE TABLE IF NOT EXISTS users (
  id          SERIAL PRIMARY KEY,
  email       TEXT UNIQUE NOT NULL,
  pass_hash   TEXT NOT NULL,
  name        TEXT NOT NULL DEFAULT '',
  is_admin    BOOLEAN NOT NULL DEFAULT FALSE,
  is_disabled BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS profiles (
  id         SERIAL PRIMARY KEY,
  user_id    INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name       TEXT NOT NULL DEFAULT '',
  type       TEXT NOT NULL DEFAULT 'xtream',
  url        TEXT NOT NULL DEFAULT '',
  username   TEXT NOT NULL DEFAULT '',
  password   TEXT NOT NULL DEFAULT '',
  mac        TEXT NOT NULL DEFAULT '',
  epg_url    TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS favs (
  user_id   INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  acc_key   TEXT NOT NULL DEFAULT '',
  type      TEXT NOT NULL,
  item_id   TEXT NOT NULL,
  name      TEXT NOT NULL DEFAULT '',
  img       TEXT NOT NULL DEFAULT '',
  data      TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, acc_key, type, item_id)
);

CREATE TABLE IF NOT EXISTS history (
  user_id   INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  acc_key   TEXT NOT NULL DEFAULT '',
  type      TEXT NOT NULL,
  item_id   TEXT NOT NULL,
  name      TEXT NOT NULL DEFAULT '',
  img       TEXT NOT NULL DEFAULT '',
  data      TEXT NOT NULL DEFAULT '',
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, acc_key, type, item_id)
);

CREATE TABLE IF NOT EXISTS positions (
  user_id   INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  acc_key   TEXT NOT NULL DEFAULT '',
  item_key  TEXT NOT NULL,
  pos       BIGINT NOT NULL DEFAULT 0,
  dur       BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, acc_key, item_key)
);

CREATE INDEX IF NOT EXISTS idx_profiles_user ON profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_favs_user ON favs(user_id);
CREATE INDEX IF NOT EXISTS idx_history_user ON history(user_id);
CREATE INDEX IF NOT EXISTS idx_positions_user ON positions(user_id);

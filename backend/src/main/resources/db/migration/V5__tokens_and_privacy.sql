CREATE TABLE email_verification_tokens (
  id          uuid                       PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid                       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  purpose     email_verification_purpose NOT NULL,
  email       text                       NOT NULL,   -- the address being confirmed, lowercased
  token_hash  char(64)                   NOT NULL,
  created_at  timestamptz                NOT NULL DEFAULT now(),
  expires_at  timestamptz                NOT NULL,
  consumed_at timestamptz,                           -- NULL means live

  CONSTRAINT verification_expiry_check CHECK (expires_at > created_at),
  CONSTRAINT verification_email_length_check CHECK (char_length(email) BETWEEN 1 AND 254)
);
CREATE UNIQUE INDEX verification_token_hash_key ON email_verification_tokens (token_hash);
CREATE UNIQUE INDEX verification_live_key ON email_verification_tokens (user_id, purpose)
  WHERE consumed_at IS NULL;
CREATE INDEX verification_live_idx ON email_verification_tokens (user_id)
  WHERE consumed_at IS NULL;

CREATE TABLE password_reset_tokens (
  id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  token_hash  char(64)    NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  expires_at  timestamptz NOT NULL,
  consumed_at timestamptz,

  CONSTRAINT password_reset_expiry_check CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX password_reset_token_hash_key ON password_reset_tokens (token_hash);
CREATE INDEX password_reset_live_idx ON password_reset_tokens (user_id) WHERE consumed_at IS NULL;

CREATE TABLE privacy_settings (
  actor_id          uuid    PRIMARY KEY REFERENCES actor (id) ON DELETE CASCADE,
  show_job_postings boolean NOT NULL DEFAULT true
);

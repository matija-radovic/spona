CREATE TABLE actor (
  id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_type varchar(16) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT actor_type_check CHECK (actor_type IN ('user', 'org'))
);


CREATE TABLE app_user (
  id                 uuid          PRIMARY KEY REFERENCES actor (id) ON DELETE CASCADE,
  username           text          NOT NULL,
  full_name          text          NOT NULL,
  email              text          NOT NULL,
  password_digest    text          NOT NULL,
  email_verified_at  timestamptz,                -- NULL = registered but locked out
  bio                text,
  avatar_url         varchar(2048),              -- machine-written by POST /uploads/avatar
  location           text,
  other_contact_info jsonb         NOT NULL DEFAULT '[]'::jsonb,

  CONSTRAINT user_username_check CHECK (
    char_length(username) BETWEEN 3 AND 30 AND username ~ '^[a-z0-9_-]+$'),
  CONSTRAINT user_full_name_length_check CHECK (char_length(full_name) <= 120),
  CONSTRAINT user_email_length_check CHECK (char_length(email) <= 254),
  CONSTRAINT user_bio_length_check CHECK (
    bio IS NULL OR char_length(btrim(bio)) BETWEEN 1 AND 500),
  CONSTRAINT user_location_length_check CHECK (
    location IS NULL OR char_length(btrim(location)) BETWEEN 1 AND 120),
  CONSTRAINT user_contact_info_check CHECK (contact_information_ok(other_contact_info))
);

CREATE UNIQUE INDEX user_username_key ON app_user (username);
CREATE UNIQUE INDEX user_email_key    ON app_user (lower(email));

-- The unverified sweep.
CREATE INDEX user_unverified_idx ON app_user (email_verified_at) WHERE email_verified_at IS NULL;


CREATE TABLE organization (
  id                 uuid          PRIMARY KEY REFERENCES actor (id) ON DELETE CASCADE,
  slug               text          NOT NULL,
  display_name       text          NOT NULL,
  legal_name         text          NOT NULL,
  description        text,
  website            text,
  avatar_url         varchar(2048),
  location           text,
  other_contact_info jsonb         NOT NULL DEFAULT '[]'::jsonb,

  CONSTRAINT org_slug_check CHECK (
    char_length(slug) BETWEEN 3 AND 40 AND slug ~ '^[a-z0-9_-]+$'),
  CONSTRAINT org_display_name_length_check CHECK (
    char_length(btrim(display_name)) BETWEEN 1 AND 120),
  CONSTRAINT org_legal_name_length_check CHECK (
    char_length(btrim(legal_name)) BETWEEN 1 AND 160),
  CONSTRAINT org_description_length_check CHECK (
    description IS NULL OR char_length(btrim(description)) BETWEEN 1 AND 1000),
  CONSTRAINT org_website_length_check CHECK (
    website IS NULL OR char_length(btrim(website)) BETWEEN 1 AND 200),
  CONSTRAINT org_location_length_check CHECK (
    location IS NULL OR char_length(btrim(location)) BETWEEN 1 AND 120),
  CONSTRAINT org_contact_info_check CHECK (contact_information_ok(other_contact_info))
);

CREATE UNIQUE INDEX organization_slug_key ON organization (slug);


-- Only a user follows; the followee may be either subtype.
CREATE TABLE follows (
  follower_id uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  followee_id uuid        NOT NULL REFERENCES actor (id)    ON DELETE CASCADE,
  created_at  timestamptz NOT NULL DEFAULT now(),

  PRIMARY KEY (follower_id, followee_id),
  CONSTRAINT follows_no_self_check CHECK (follower_id <> followee_id)
);

-- Follower lists and counts.
CREATE INDEX follows_followee_idx ON follows (followee_id);

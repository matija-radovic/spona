CREATE TABLE post (
  id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  content        text        NOT NULL,
  created_by_id  uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  created_for_id uuid        NOT NULL REFERENCES actor (id)    ON DELETE CASCADE,
  created_at     timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT post_content_length_check CHECK (char_length(btrim(content)) BETWEEN 1 AND 5000)
);
CREATE INDEX post_actor_idx ON post (created_for_id, created_at DESC);

CREATE TABLE comment (
  id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  content                text        NOT NULL,
  -- Depth is capped at 1 by the application, not by the schema: a reply cannot
  -- itself receive replies.
  response_to_comment_id uuid        REFERENCES comment (id) ON DELETE CASCADE,
  part_of_post_id        uuid        NOT NULL REFERENCES post (id)     ON DELETE CASCADE,
  created_by_id          uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  created_for_id         uuid        NOT NULL REFERENCES actor (id)    ON DELETE CASCADE,
  created_at             timestamptz NOT NULL DEFAULT now(),

  CONSTRAINT comment_content_length_check CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);
CREATE INDEX comment_post_idx  ON comment (part_of_post_id, created_at);
CREATE INDEX comment_actor_idx ON comment (created_for_id, created_at DESC);

CREATE TABLE reaction_target_types (
  target_type_id uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  table_name     varchar(63) NOT NULL,   -- a Postgres identifier's own limit
  display_name   varchar(64) NOT NULL
);
CREATE UNIQUE INDEX reaction_target_type_name_key ON reaction_target_types (table_name);

CREATE TABLE reaction (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  emoji          text NOT NULL,
  target_type_id uuid NOT NULL REFERENCES reaction_target_types (target_type_id),
  target_id      uuid NOT NULL,
  created_by_id  uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,

  CONSTRAINT reaction_emoji_length_check CHECK (char_length(emoji) BETWEEN 1 AND 16)
);
CREATE UNIQUE INDEX reaction_once_key ON reaction (target_type_id, target_id, created_by_id, emoji);
CREATE INDEX reaction_target_idx ON reaction (target_type_id, target_id);

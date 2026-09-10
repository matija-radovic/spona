CREATE TABLE job (
  id                  uuid             PRIMARY KEY DEFAULT gen_random_uuid(),
  slug                varchar(160)     NOT NULL,   -- server-built from the title plus a suffix
  title               text             NOT NULL,
  description         text             NOT NULL,
  keywords            text[]           NOT NULL DEFAULT '{}',

  category            job_category,
  job_type            job_type,
  workplace_type      workplace_type,
  working_hours       working_hours,
  experience_level    experience_level,

  location            text             NOT NULL,   -- set even for REMOTE: where the org sits
  status              job_status       NOT NULL,

  application_type    application_type NOT NULL,
  contact_information jsonb,
  external_url        text,

  created_by_id       uuid             NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  created_for_id      uuid             NOT NULL REFERENCES actor (id)    ON DELETE CASCADE,
  created_at          timestamptz      NOT NULL DEFAULT now(),
  updated_at          timestamptz      NOT NULL DEFAULT now(),

  search_vector tsvector GENERATED ALWAYS AS (
      setweight(to_tsvector('sr', coalesce(title, '')),                      'A') ||
      setweight(to_tsvector('sr', immutable_array_to_string(keywords, ' ')), 'B') ||
      setweight(to_tsvector('sr', coalesce(description, '')),                'C')
  ) STORED,

  CONSTRAINT job_external_url_pairing_check CHECK (
    (application_type = 'EXTERNAL_URL') = (external_url IS NOT NULL)),
  CONSTRAINT job_contact_pairing_check CHECK (
    (application_type = 'CONTACT_ONLY') = (contact_information IS NOT NULL)),
  CONSTRAINT job_keywords_check CHECK (job_keywords_ok(keywords)),
  CONSTRAINT job_text_length_check CHECK (
        (btrim(title) = ''       OR char_length(btrim(title))       BETWEEN 3 AND 120)
    AND (btrim(description) = '' OR char_length(btrim(description)) BETWEEN 40 AND 5000)
    AND char_length(btrim(location)) <= 120
    AND (external_url IS NULL OR char_length(btrim(external_url)) <= 300)),
  CONSTRAINT job_publishable_check CHECK (
    status = 'DRAFT'
    OR (category IS NOT NULL AND job_type IS NOT NULL AND workplace_type IS NOT NULL
        AND char_length(btrim(title)) >= 3
        AND char_length(btrim(description)) >= 40
        AND char_length(btrim(location)) >= 1
        AND (application_type <> 'CONTACT_ONLY'
             OR jsonb_array_length(contact_information) >= 1)
        AND (application_type <> 'EXTERNAL_URL'
             OR char_length(btrim(external_url)) >= 1))),
  CONSTRAINT job_contact_info_check CHECK (
    contact_information IS NULL OR contact_information_ok(contact_information))
);
CREATE UNIQUE INDEX job_slug_key ON job (slug);
CREATE INDEX job_status_created_idx ON job (status, created_at DESC);
CREATE INDEX job_status_category_idx ON job (status, category);
CREATE INDEX job_actor_status_idx ON job (created_for_id, status);
CREATE INDEX job_search_idx   ON job USING GIN (search_vector);
CREATE INDEX job_keywords_idx ON job USING GIN (keywords);
CREATE INDEX job_title_trgm ON job USING GIN (immutable_unaccent(title) gin_trgm_ops);

CREATE TABLE salary (
  job_id       uuid          PRIMARY KEY REFERENCES job (id) ON DELETE CASCADE,
  min          numeric(12,2) NOT NULL,
  max          numeric(12,2),
  currency     text          NOT NULL,
  period       salary_period NOT NULL,
  display_text text,

  CONSTRAINT salary_min_check CHECK (min >= 0),
  CONSTRAINT salary_max_check CHECK (max IS NULL OR max >= min),
  CONSTRAINT salary_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT salary_display_text_length_check CHECK (
    display_text IS NULL OR char_length(btrim(display_text)) BETWEEN 1 AND 60)
);

CREATE TABLE job_application (
  id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id        uuid         NOT NULL REFERENCES job (id)      ON DELETE CASCADE,
  cv_object_key varchar(512) NOT NULL,
  submitted_at  timestamptz  NOT NULL DEFAULT now(),
  created_by_id uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE
);
CREATE INDEX job_application_inbox_idx ON job_application (job_id, submitted_at DESC);

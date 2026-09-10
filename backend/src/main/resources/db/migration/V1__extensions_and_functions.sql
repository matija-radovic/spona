CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- The search configuration.
CREATE TEXT SEARCH CONFIGURATION sr (COPY = serbian);

ALTER TEXT SEARCH CONFIGURATION sr
  ALTER MAPPING FOR hword, hword_part, word, asciiword, asciihword
  WITH unaccent, serbian_stem;

CREATE FUNCTION immutable_unaccent(text) RETURNS text
  LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
  $$ SELECT public.unaccent('public.unaccent', $1) $$;

CREATE FUNCTION immutable_array_to_string(text[], text) RETURNS text
  LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
  $$ SELECT array_to_string($1, $2) $$;

CREATE FUNCTION job_keywords_ok(kw text[]) RETURNS boolean
  LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
  SELECT cardinality(kw) <= 10
     AND (SELECT coalesce(bool_and(char_length(k) BETWEEN 1 AND 32), true)
            FROM unnest(kw) k)
$$;

CREATE FUNCTION contact_information_ok(entries jsonb) RETURNS boolean
  LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
  SELECT jsonb_typeof(entries) = 'array'
     AND jsonb_array_length(entries) <= 15
     AND NOT EXISTS (
           SELECT 1 FROM jsonb_array_elements(entries) e
            WHERE jsonb_typeof(e)          <> 'object'
               OR jsonb_typeof(e->'type')  <> 'string'
               OR jsonb_typeof(e->'value') <> 'string'
               OR char_length(e->>'type')  NOT BETWEEN 1 AND 32
               OR char_length(e->>'value') NOT BETWEEN 1 AND 128
               OR char_length(coalesce(e->>'description', '')) > 32)
$$;

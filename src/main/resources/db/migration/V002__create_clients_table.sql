CREATE TABLE clients
(
  id          uuid        PRIMARY KEY,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  state       text        NOT NULL DEFAULT 'ACTIVE',
  first_name  text        NOT NULL,
  last_name   text        NOT NULL,
  email       text        NOT NULL,
  description text,
  search      tsvector
    GENERATED ALWAYS AS (
      setweight(to_tsvector('english', coalesce(first_name, '')), 'A') ||
      setweight(to_tsvector('english', coalesce(last_name, '')), 'A') ||
      setweight(to_tsvector('english', regexp_replace(coalesce(email, ''), '[@._+\-]+', ' ', 'g')), 'B') ||
      setweight(to_tsvector('english', coalesce(description, '')), 'C')
      ) STORED
);

CREATE UNIQUE INDEX clients_email_unique_idx
  ON clients (lower(email))
  WHERE state = 'ACTIVE';

CREATE INDEX clients_search_idx
  ON clients USING GIN (search);

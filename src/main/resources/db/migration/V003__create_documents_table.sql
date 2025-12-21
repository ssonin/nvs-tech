CREATE TABLE documents
(
  id         uuid        PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  state      text        NOT NULL DEFAULT 'ACTIVE',
  client_id  uuid        NOT NULL,
  title      text        NOT NULL,
  content    text        NOT NULL,
  search     tsvector
    GENERATED ALWAYS AS (
      setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
      to_tsvector('english', coalesce(content, ''))
      ) STORED
);

CREATE INDEX documents_client_id_idx
  ON documents (client_id);

CREATE INDEX documents_search_idx
  ON documents USING GIN (search);

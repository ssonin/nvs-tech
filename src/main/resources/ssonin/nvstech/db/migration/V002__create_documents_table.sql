CREATE TABLE documents
(
  id         uuid        PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  state      text        NOT NULL DEFAULT 'ACTIVE',
  client_id  uuid        NOT NULL,
  title      text        NOT NULL,
  content    text        NOT NULL
);

CREATE INDEX documents_client_id_idx
  ON documents (client_id);

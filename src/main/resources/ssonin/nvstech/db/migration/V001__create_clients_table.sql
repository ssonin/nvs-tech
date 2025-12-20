CREATE TABLE clients
(
  id          uuid        PRIMARY KEY,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  state       text        NOT NULL DEFAULT 'ACTIVE',
  first_name  text        NOT NULL,
  last_name   text        NOT NULL,
  email       text        NOT NULL,
  description text
);

CREATE UNIQUE INDEX clients_email_unique_idx
  ON clients (lower(email))
  WHERE state = 'ACTIVE';

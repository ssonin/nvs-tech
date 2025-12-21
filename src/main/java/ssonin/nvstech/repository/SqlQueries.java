package ssonin.nvstech.repository;

interface SqlQueries {

  static String insertClient() {
    return """
      INSERT INTO clients (id, first_name, last_name, email, description)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING id, created_at, first_name, last_name, email, description;
      """;
  }

  static String selectClient() {
    return """
      SELECT id, created_at, first_name, last_name, email, description
      FROM clients
      WHERE id = $1;
      """;
  }

  static String insertDocument() {
    return """
      INSERT INTO documents (id, client_id, title, content)
      VALUES ($1, $2, $3, $4)
      RETURNING id, created_at, client_id, title, content;
      """;
  }

  static String searchClients() {
    return """
      SELECT
        'client' AS type,
        id,
        created_at,
        first_name,
        last_name,
        email,
        description,
        ts_rank(search, query) AS rank
      FROM clients, plainto_tsquery('english', $1) query
      WHERE search @@ query
      ORDER BY rank DESC;
      """;
  }

  static String searchDocuments() {
    return """
      SELECT
        'document' AS type,
        id,
        created_at,
        client_id,
        title,
        content,
        ts_rank(search, query) AS rank
      FROM documents, plainto_tsquery('english', $1) query
      WHERE search @@ query
      ORDER BY rank DESC;
    """;
  }
}

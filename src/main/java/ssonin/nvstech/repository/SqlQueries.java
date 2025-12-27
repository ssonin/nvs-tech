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
      INSERT INTO documents (id, client_id, title, content, embedding)
      VALUES ($1, $2, $3, $4, $5::vector)
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
    WITH fts_results AS (
      SELECT id, ts_rank(search, plainto_tsquery('english', $1)) AS rank
      FROM documents
      WHERE search @@ plainto_tsquery('english', $1)
    ),
    vector_results AS (
      SELECT id, 1 - (embedding <=> $2::vector) AS rank
      FROM documents
      WHERE embedding IS NOT NULL
      ORDER BY rank
      LIMIT 20
    ),
    combined AS (
      SELECT id, MAX(rank) AS rank
      FROM (
        SELECT * FROM fts_results
        UNION ALL
        SELECT * FROM vector_results
      ) sub
      GROUP BY id
    )
    SELECT
      'document' AS type,
      d.id,
      d.created_at,
      d.client_id,
      d.title,
      d.content,
      c.rank
    FROM combined c
    JOIN documents d ON d.id = c.id
    ORDER BY c.rank DESC
    LIMIT 20;
    """;
  }
}

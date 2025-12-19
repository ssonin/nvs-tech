package ssonin.nvstech.repository;

interface SqlQueries {

  static String insertClient() {
    return """
      INSERT INTO clients (id, first_name, last_name, email, description)
      VALUES ($1, $2, $3, $4, $5)
      RETURNING id, created_date, first_name, last_name, email, description""";
  }
}

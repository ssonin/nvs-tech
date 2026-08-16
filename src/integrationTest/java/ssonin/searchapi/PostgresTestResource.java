package ssonin.searchapi;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector.MatchesType;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static io.vertx.mutiny.core.Vertx.vertx;
import static io.vertx.mutiny.pgclient.PgBuilder.pool;
import static org.testcontainers.postgresql.PostgreSQLContainer.POSTGRESQL_PORT;

public final class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

  private PostgreSQLContainer postgres;
  private Vertx vertx;
  private Pool pool;

  @Override
  public Map<String, String> start() {
    postgres = createPostgresContainer();
    vertx = vertx();
    var options = getConnectOptions(postgres);
    pool = pool()
      .using(vertx)
      .connectingTo(options)
      .with(new PoolOptions().setMaxSize(2))
      .build();

    return Map.of(
      "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
      "quarkus.datasource.reactive.url", "postgresql://%s:%d/%s".formatted(options.getHost(), options.getPort(), options.getDatabase()),
      "quarkus.datasource.username", options.getUser(),
      "quarkus.datasource.password", options.getPassword()
    );
  }

  @Override
  public void stop() {
    if (pool != null) {
      pool.closeAndAwait();
      pool = null;
    }
    if (vertx != null) {
      vertx.closeAndAwait();
      vertx = null;
    }
    if (postgres != null) {
      postgres.stop();
      postgres = null;
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(pool, new MatchesType(Pool.class));
  }

  private static PostgreSQLContainer createPostgresContainer() {
    var container = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.6-pg16"))
      .withDatabaseName("search_api_test")
      .withUsername("search_api_user")
      .withPassword("search_api_password")
      .withCommand(
        "postgres",
        "-c", "wal_level=logical",
        "-c", "max_replication_slots=10",
        "-c", "max_wal_senders=10"
      );
    container.start();
    return container;
  }

  private static PgConnectOptions getConnectOptions(PostgreSQLContainer container) {
    return new PgConnectOptions()
      .setHost(container.getHost())
      .setPort(container.getMappedPort(POSTGRESQL_PORT))
      .setDatabase(container.getDatabaseName())
      .setUser(container.getUsername())
      .setPassword(container.getPassword());
  }
}

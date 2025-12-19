package ssonin.nvstech;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import ssonin.nvstech.api.ApiVerticle;
import ssonin.nvstech.repository.RepositoryVerticle;

import java.util.Optional;

import static java.util.function.Predicate.not;

public final class App extends VerticleBase {

  public static final int DEFAULT_HTTP_PORT = 8888;

  @Override
  public Future<?> start() {
    final var dbConfig = PgConnectOptions.fromEnv();
    final var config = new JsonObject()
      .put("http.port", httpPort())
      .put("db", dbConfig.toJson());
    final var options = new DeploymentOptions().setConfig(config);
    return vertx.executeBlocking(() -> runDbMigration(dbConfig))
      .compose(__ -> Future.all(
        vertx.deployVerticle(new ApiVerticle(), options),
        vertx.deployVerticle(new RepositoryVerticle(), options))
      );
  }

  private MigrateResult runDbMigration(PgConnectOptions db) {
    final var url = "jdbc:postgresql://%s:%d/%s".formatted(db.getHost(), db.getPort(), db.getDatabase());
    return Flyway.configure()
      .dataSource(url, db.getUser(), db.getPassword())
      .locations("classpath:ssonin/nvstech/db/migration")
      .schemas("public")
      .load()
      .migrate();
  }

  private static int httpPort() {
    return Optional.ofNullable(System.getenv("HTTP_PORT"))
      .or(() -> Optional.ofNullable(System.getenv("PORT")))
      .filter(not(String::isBlank))
      .map(Integer::parseInt)
      .orElse(DEFAULT_HTTP_PORT);
  }
}

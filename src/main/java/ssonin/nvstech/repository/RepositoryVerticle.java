package ssonin.nvstech.repository;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.VerticleBase;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;

import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.nvstech.repository.SqlQueries.insertClient;

public final class RepositoryVerticle extends VerticleBase {

  private static final Logger LOG = getLogger(RepositoryVerticle.class);

  private Pool pool;

  @Override
  public Future<?> start() {
    final var dbConfig = new PgConnectOptions(config().getJsonObject("db"));
    pool = PgBuilder
      .pool()
      .connectingTo(dbConfig)
      .with(new PoolOptions())
      .using(vertx)
      .build();
    final var eb = vertx.eventBus();
    eb.consumer("client.create", this::createClient);
    return succeededFuture();
  }

  private void createClient(Message<JsonObject> msg) {
    final var data = msg.body();
    final var values = Tuple.of(
      randomUUID(),
      data.getString("first_name"),
      data.getString("last_name"),
      data.getString("email"),
      data.getString("description"));
    pool
      .withConnection(conn ->
        conn.preparedQuery(insertClient())
          .execute(values)
          .map(rows -> {
            final var rs = rows.iterator().next();
            return new JsonObject()
              .put("id", rs.getUUID("id").toString())
              .put("first_name", rs.getString("first_name"))
              .put("last_name", rs.getString("last_name"))
              .put("email", rs.getString("email"))
              .put("description", rs.getString("description"));
          }))
      .onSuccess(msg::reply)
      .onFailure(handleError(msg));
  }

  private static Handler<Throwable> handleError(Message<JsonObject> msg) {
    return e -> {
      LOG.error("Failed to create client", e);
      if (duplicateKeyInsert(e)) {
        msg.fail(422, "Email is already in use");
      } else {
        msg.fail(500, "Something went wrong");
      }
    };
  }

  private static boolean duplicateKeyInsert(Throwable e) {
    if (e instanceof PgException pgException) {
      return "23505".equals(pgException.getSqlState());
    }
    return false;
  }
}

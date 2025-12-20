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
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;

import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static org.slf4j.LoggerFactory.getLogger;
import static ssonin.nvstech.repository.SqlQueries.insertClient;
import static ssonin.nvstech.repository.SqlQueries.selectClient;

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
    eb.consumer("client.get", this::getClient);
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
          .map(rows -> clientFromRow(rows.iterator().next())))
      .onSuccess(msg::reply)
      .onFailure(handleError(msg));
  }

  private void getClient(Message<JsonObject> msg) {
    final var clientId = msg.body().getString("clientId");
    pool
      .withConnection(conn ->
        conn.preparedQuery(selectClient())
          .execute(Tuple.of(clientId))
          .map(rows -> {
            final var it = rows.iterator();
            if (it.hasNext()) {
              return clientFromRow(it.next());
            }
            throw new ClientNotFoundException();
          }))
      .onSuccess(msg::reply)
      .onFailure(handleError(msg));
  }

  private JsonObject clientFromRow(Row row) {
    return new JsonObject()
      .put("id", row.getUUID("id").toString())
      .put("first_name", row.getString("first_name"))
      .put("last_name", row.getString("last_name"))
      .put("email", row.getString("email"))
      .put("description", row.getString("description"));
  }

  private static Handler<Throwable> handleError(Message<JsonObject> msg) {
    return e -> {
      if (duplicateKeyInsert(e)) {
        msg.fail(409, "Email is already in use");
      } else if (e instanceof NotFoundException){
        msg.fail(404, e.getMessage());
      }
      else {
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

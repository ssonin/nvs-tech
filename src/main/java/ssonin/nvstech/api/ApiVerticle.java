package ssonin.nvstech.api;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public final class ApiVerticle extends VerticleBase {

  private static final Logger LOG = getLogger(ApiVerticle.class);
  private static final String API_V_1 = "/api/v1";

  @Override
  public Future<?> start() {
    final var router = Router.router(vertx);
    router
      .route()
      .handler(this::log);
    router
      .get(API_V_1 + "/clients/:clientId")
      .handler(this::fetchClient);
    return vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(config().getInteger("http.port"))
      .onSuccess(httpServer -> LOG.info("HTTP server started on port {}", httpServer.actualPort()));
  }

  private void log(RoutingContext ctx) {
    LOG.info("Received request {}", ctx.request().path());
    ctx.next();
  }

  private void fetchClient(RoutingContext ctx) {
    final var userId = ctx.pathParam("clientId");
    final var payload = new JsonObject()
      .put("id", userId)
      .put("first_name", "Foo")
      .put("last_name", "Bar")
      .put("email", "foobar@nvs.com")
      .put("description", "Bamboozled");
    ctx.response()
      .putHeader("Content-Type", "application/json")
      .end(payload.encode());
  }
}

package ssonin.nvstech.api;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
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
      .handler(LoggerHandler.create());
    router
      .post()
      .handler(BodyHandler.create());
    router
      .post(API_V_1 + "/clients")
      .handler(this::createClient);
    router
      .get(API_V_1 + "/clients/:clientId")
      .handler(this::fetchClient);
    router
      .route()
      .failureHandler(this::handleError);
    return vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(config().getInteger("http.port"))
      .onSuccess(httpServer -> LOG.info("HTTP server started on port {}", httpServer.actualPort()));
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

  private void createClient(RoutingContext ctx) {
    final var payload = ctx.body().asJsonObject();
    final var eb = vertx.eventBus();
    eb.<JsonObject>request("client.create", payload)
      .onSuccess(reply ->
        ctx.response()
          .setStatusCode(201)
          .putHeader("Location", "%s/%s".formatted(ctx.request().absoluteURI(), reply.body().getString("id")))
          .putHeader("Content-Type", "application/json")
          .end(reply.body().toString()))
      .onFailure(ctx::fail);
  }

  private void handleError(RoutingContext ctx) {
    final var e = ctx.failure();
    if (e instanceof ReplyException re) {
      ctx.response()
        .setStatusCode(re.failureCode())
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", re.getMessage()).encode());
      return;
    }
    LOG.error("Unhandled error", e);
    ctx.response()
      .setStatusCode(500)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", "Internal server error").encode());
  }
}

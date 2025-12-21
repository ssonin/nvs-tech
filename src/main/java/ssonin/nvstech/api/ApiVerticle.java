package ssonin.nvstech.api;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;

import java.util.UUID;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
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
      .handler(this::getClient);
    router
      .post(API_V_1 + "/clients/:clientId/documents")
      .handler(this::createDocument);
    router
      .get(API_V_1 + "/search")
      .handler(this::search);
    router
      .route()
      .failureHandler(this::handleError);
    return vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(config().getInteger("http.port"))
      .onSuccess(httpServer -> LOG.info("HTTP server started on port {}", httpServer.actualPort()));
  }

  private void createClient(RoutingContext ctx) {
    final var payload = ctx.body().asJsonObject();
    vertx.eventBus()
      .<JsonObject>request("clients.create", payload)
      .onSuccess(reply ->
        ctx.response()
          .setStatusCode(201)
          .putHeader("Location", "%s/%s".formatted(ctx.request().absoluteURI(), reply.body().getString("id")))
          .putHeader("Content-Type", "application/json")
          .end(reply.body().toString()))
      .onFailure(ctx::fail);
  }

  private void getClient(RoutingContext ctx) {
    fetchClient(ctx)
      .onSuccess(reply ->
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(reply.body().toString()))
      .onFailure(ctx::fail);
  }

  private void createDocument(RoutingContext ctx) {
    fetchClient(ctx)
      .compose(client -> {
        final var payload = ctx.body().asJsonObject();
        payload.put("client_id", client.body().getString("id"));
        return vertx.eventBus()
          .<JsonObject>request("documents.create", payload);
      })
      .onSuccess(reply ->
        ctx.response()
          .setStatusCode(201)
          .putHeader("Location", "%s/%s".formatted(ctx.request().absoluteURI(), reply.body().getString("id")))
          .putHeader("Content-Type", "application/json")
          .end(reply.body().toString()))
      .onFailure(ctx::fail);
  }

  private void search(RoutingContext ctx) {
    final var query = ctx.request().getParam("q").toLowerCase();
    final var payload = new JsonObject().put("query", query);
    vertx.eventBus()
      .<JsonArray>request("search", payload)
      .onSuccess(reply ->
        ctx.response()
          .setStatusCode(200)
          .putHeader("Content-Type", "application/json")
          .end(reply.body().toString()))
      .onFailure(ctx::fail);
  }

  private Future<Message<JsonObject>> fetchClient(RoutingContext ctx) {
    return uuidPathParam(ctx)
      .compose(clientId -> {
        final var payload = new JsonObject().put("clientId", clientId.toString());
        return vertx.eventBus()
          .request("clients.get", payload);
      });
  }

  private Future<UUID> uuidPathParam(RoutingContext ctx) {
    final var clientId = ctx.pathParam("clientId");
    try {
      return succeededFuture(UUID.fromString(clientId));
    } catch (IllegalArgumentException e) {
      LOG.warn("Invalid client ID: {}", clientId);
      return failedFuture(new HttpException(400, "Invalid client ID", e));
    }
  }

  private void handleError(RoutingContext ctx) {
    final var failure = ctx.failure();
    if (failure instanceof ReplyException e) {
      ctx.response()
        .setStatusCode(e.failureCode())
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", e.getMessage()).encode());
      return;
    }
    if (failure instanceof HttpException e) {
      ctx.response()
        .setStatusCode(e.getStatusCode())
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", e.getPayload()).encode());
      return;
    }
    LOG.error("Unhandled error", failure);
    ctx.response()
      .setStatusCode(500)
      .putHeader("Content-Type", "application/json")
      .end(new JsonObject().put("error", "Internal server error").encode());
  }
}

package ssonin.nvstech;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.json.JsonObject;
import ssonin.nvstech.api.ApiVerticle;

import java.util.Optional;

import static java.util.function.Predicate.not;

public final class App extends VerticleBase {

  public static final int DEFAULT_HTTP_PORT = 8888;

  @Override
  public Future<?> start() {
    final var config = new JsonObject().put("http.port", httpPort());
    final var options = new DeploymentOptions().setConfig(config);
    return vertx.deployVerticle(new ApiVerticle(), options);
  }

  private static int httpPort() {
    return Optional.ofNullable(System.getenv("HTTP_PORT"))
      .or(() -> Optional.ofNullable(System.getenv("PORT")))
      .filter(not(String::isBlank))
      .map(Integer::parseInt)
      .orElse(DEFAULT_HTTP_PORT);
  }
}

package ssonin.nvstech;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public final class App extends VerticleBase {

  private static final Logger LOG = getLogger(App.class);

  @Override
  public Future<?> start() {
    return vertx.createHttpServer().requestHandler(req -> {
      LOG.info("Received request {} ", req.path());
      req.response()
        .putHeader("content-type", "text/plain")
        .end("Hello from Vert.x!");
    }).listen(8888).onSuccess(http -> {
      LOG.info("HTTP server started on port 8888");
    });
  }
}

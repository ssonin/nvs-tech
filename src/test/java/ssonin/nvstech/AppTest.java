package ssonin.nvstech;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith({SystemStubsExtension.class, VertxExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {

  private static final String THESAURUS_CONTENT = """
      address proof : address_proof
      utility bill  : address_proof
      """;

  private static final String THESAURUS_PATH = "/usr/local/share/postgresql/tsearch_data/custom_thesaurus.ths";

  private static final int TEST_HTTP_PORT = 9999;

  @Container
  private static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("nvs_tech_test")
      .withUsername("test_user")
      .withPassword("test_password");

  @SystemStub
  private static EnvironmentVariables environmentVariables;

  private WebClient webClient;
  private String deploymentId;

  @BeforeAll
  void setup(Vertx vertx, VertxTestContext ctx) {
    postgres.copyFileToContainer(
      Transferable.of(THESAURUS_CONTENT),
      THESAURUS_PATH);

    environmentVariables
      .set("PGHOST", postgres.getHost())
      .set("PGPORT", String.valueOf(postgres.getMappedPort(5432)))
      .set("PGDATABASE", postgres.getDatabaseName())
      .set("PGUSER", postgres.getUsername())
      .set("PGPASSWORD", postgres.getPassword())
      .set("HTTP_PORT", String.valueOf(TEST_HTTP_PORT));

    webClient = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(TEST_HTTP_PORT));

    vertx.deployVerticle(new App())
      .onComplete(ctx.succeeding(id -> {
        deploymentId = id;
        ctx.completeNow();
      }));
  }

  @AfterAll
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    if (webClient != null) {
      webClient.close();
    }
    if (deploymentId != null) {
      vertx.undeploy(deploymentId)
        .onComplete(ctx.succeedingThenComplete());
    } else {
      ctx.completeNow();
    }
  }

  @Test
  @Order(1)
  @DisplayName("App deployment: must complete successfully with all verticles deployed")
  void appDeployment_mustCompleteSuccessfully(VertxTestContext ctx) {
    ctx.verify(() -> {
      assertThat(deploymentId)
        .as("Deployment ID must be present after successful deployment")
        .isNotNull()
        .isNotBlank();
      ctx.completeNow();
    });
  }

  @Test
  @Order(2)
  @DisplayName("HTTP server: must be running and accepting requests")
  void httpServer_mustBeRunningAndAcceptingRequests(VertxTestContext ctx) {
    webClient.get("/api/v1/search")
      .addQueryParam("q", "test")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("HTTP server must respond with 200 for search endpoint")
          .isEqualTo(200);
        assertThat(response.getHeader("Content-Type"))
          .as("Response must have JSON content type")
          .isEqualTo("application/json");
        ctx.completeNow();
      })));
  }

  @Test
  @Order(3)
  @DisplayName("Database migration: must have run successfully with tables created")
  void databaseMigration_mustHaveRunSuccessfully(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Integration")
      .put("last_name", "Test")
      .put("email", "integration.test@example.com")
      .put("description", "Test client for integration testing");

    webClient.post("/api/v1/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Must be able to create client, confirming database tables exist")
          .isEqualTo(201);
        assertThat(response.bodyAsJsonObject().getString("id"))
          .as("Created client must have an ID")
          .isNotNull();
        ctx.completeNow();
      })));
  }

  @Test
  @Order(4)
  @DisplayName("ApiVerticle: must be deployed and handling client retrieval")
  void apiVerticle_mustBeDeployedAndHandlingRequests(VertxTestContext ctx) {
    var nonExistentClientId = "00000000-0000-0000-0000-000000000000";

    webClient.get("/api/v1/clients/" + nonExistentClientId)
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("ApiVerticle must handle client retrieval and return 404 for non-existent client")
          .isEqualTo(404);
        assertThat(response.bodyAsJsonObject().getString("error"))
          .as("Error response must contain appropriate message")
          .isEqualTo("Client not found");
        ctx.completeNow();
      })));
  }

  @Test
  @Order(5)
  @DisplayName("RepositoryVerticle: must be deployed and processing event bus messages")
  void repositoryVerticle_mustBeDeployedAndProcessingMessages(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Repository")
      .put("last_name", "VerticleTest")
      .put("email", "repository.verticle@example.com")
      .put("description", "Testing repository verticle deployment");

    webClient.post("/api/v1/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(createResponse -> ctx.verify(() -> {
        assertThat(createResponse.statusCode()).isEqualTo(201);

        var clientId = createResponse.bodyAsJsonObject().getString("id");

        webClient.get("/api/v1/clients/" + clientId)
          .send()
          .onComplete(ctx.succeeding(getResponse -> ctx.verify(() -> {
            assertThat(getResponse.statusCode())
              .as("RepositoryVerticle must process get client request")
              .isEqualTo(200);
            assertThat(getResponse.bodyAsJsonObject().getString("first_name"))
              .as("Retrieved client must match created client")
              .isEqualTo("Repository");
            ctx.completeNow();
          })));
      })));
  }

  @Test
  @Order(6)
  @DisplayName("Search functionality: must be operational with text search configured")
  void searchFunctionality_mustBeOperational(VertxTestContext ctx) {
    webClient.get("/api/v1/search")
      .addQueryParam("q", "Repository")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Search endpoint must return 200")
          .isEqualTo(200);
        assertThat(response.bodyAsJsonArray())
          .as("Search must return results for previously created client")
          .isNotEmpty();
        ctx.completeNow();
      })));
  }

  @Test
  @Order(7)
  @DisplayName("Document creation: must work with full request chain through all verticles")
  void documentCreation_mustWorkWithFullRequestChain(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Document")
      .put("last_name", "Owner")
      .put("email", "document.owner@example.com")
      .put("description", "Client for document creation test");

    webClient.post("/api/v1/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(clientResponse -> ctx.verify(() -> {
        assertThat(clientResponse.statusCode()).isEqualTo(201);

        var clientId = clientResponse.bodyAsJsonObject().getString("id");

        var documentData = new JsonObject()
          .put("title", "Integration Test Document")
          .put("content", "This document verifies the full request chain works correctly");

        webClient.post("/api/v1/clients/" + clientId + "/documents")
          .putHeader("Content-Type", "application/json")
          .sendJsonObject(documentData)
          .onComplete(ctx.succeeding(docResponse -> ctx.verify(() -> {
            assertThat(docResponse.statusCode())
              .as("Document creation must succeed through full verticle chain")
              .isEqualTo(201);
            assertThat(docResponse.bodyAsJsonObject().getString("id"))
              .as("Created document must have an ID")
              .isNotNull();
            assertThat(docResponse.bodyAsJsonObject().getString("client_id"))
              .as("Document must be associated with the correct client")
              .isEqualTo(clientId);
            ctx.completeNow();
          })));
      })));
  }

  @Test
  @Order(8)
  @DisplayName("Thesaurus configuration: must be applied for text search synonyms")
  void thesaurusConfiguration_mustBeApplied(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Thesaurus")
      .put("last_name", "TestClient")
      .put("email", "thesaurus.test@example.com")
      .put("description", "Client with utility bill document");

    webClient.post("/api/v1/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(clientResponse -> ctx.verify(() -> {
        var clientId = clientResponse.bodyAsJsonObject().getString("id");

        var documentData = new JsonObject()
          .put("title", "Utility Bill Statement")
          .put("content", "This is a utility bill for testing thesaurus mapping");

        webClient.post("/api/v1/clients/" + clientId + "/documents")
          .putHeader("Content-Type", "application/json")
          .sendJsonObject(documentData)
          .onComplete(ctx.succeeding(docResponse -> ctx.verify(() -> {
            assertThat(docResponse.statusCode()).isEqualTo(201);

            webClient.get("/api/v1/search")
              .addQueryParam("q", "address proof")
              .send()
              .onComplete(ctx.succeeding(searchResponse -> ctx.verify(() -> {
                assertThat(searchResponse.statusCode()).isEqualTo(200);

                var results = searchResponse.bodyAsJsonArray();
                var hasUtilityBillDocument = results.stream()
                  .map(o -> (JsonObject) o)
                  .anyMatch(r -> "document".equals(r.getString("type")) &&
                    r.getString("title").contains("Utility Bill"));

                assertThat(hasUtilityBillDocument)
                  .as("Thesaurus must map 'address proof' to find 'utility bill' document")
                  .isTrue();

                ctx.completeNow();
              })));
          })));
      })));
  }
}

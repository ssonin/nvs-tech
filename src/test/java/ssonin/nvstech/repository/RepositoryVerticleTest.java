package ssonin.nvstech.repository;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryVerticleTest {

  private static final String THESAURUS_CONTENT = """
      address proof : address_proof
      utility bill  : address_proof
      """;

  private static final String THESAURUS_PATH = "/usr/local/share/postgresql/tsearch_data/custom_thesaurus.ths";

  @Container
  private static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("nvs_tech_test")
      .withUsername("test_user")
      .withPassword("test_password");

  private String createdClientId;

  @BeforeAll
  void setup(Vertx vertx, VertxTestContext ctx) {
    postgres.copyFileToContainer(
      Transferable.of(THESAURUS_CONTENT),
      THESAURUS_PATH);

    var jdbcUrl = postgres.getJdbcUrl();
    Flyway.configure()
      .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
      .schemas("public")
      .locations("classpath:db/migration")
      .validateMigrationNaming(true)
      .load()
      .migrate();

    var dbConfig = new JsonObject()
      .put("host", postgres.getHost())
      .put("port", postgres.getMappedPort(5432))
      .put("database", postgres.getDatabaseName())
      .put("user", postgres.getUsername())
      .put("password", postgres.getPassword());

    var config = new JsonObject().put("db", dbConfig);
    var options = new DeploymentOptions().setConfig(config);

    vertx.deployVerticle(new RepositoryVerticle(), options)
      .onComplete(ctx.succeedingThenComplete());
  }

  @Test
  @Order(1)
  @DisplayName("createClient: must create a new client successfully")
  void createClient_success(Vertx vertx, VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Chandler")
      .put("last_name", "Bing")
      .put("email", "chandler.bing@neviswealth.com")
      .put("description", "Sarcastic, self-deprecating office worker with a sharp sense of humor, " +
        "known for cracking jokes to deflect awkward situations.");

    vertx.eventBus().<JsonObject>request("clients.create", clientData)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var client = reply.body();

        assertThat(client.getString("id"))
          .as("Client ID must be present")
          .isNotNull();
        assertThat(client.getString("created_at"))
          .as("created_at must be present")
          .isNotNull();
        assertThat(client.getString("first_name")).isEqualTo("Chandler");
        assertThat(client.getString("last_name")).isEqualTo("Bing");
        assertThat(client.getString("email")).isEqualTo("chandler.bing@neviswealth.com");
        assertThat(client.getString("description")).contains("Sarcastic");

        createdClientId = client.getString("id");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(2)
  @DisplayName("createClient: must return 409 when email already exists")
  void createClient_duplicateEmail_returns409(Vertx vertx, VertxTestContext ctx) {
    var duplicateClient = new JsonObject()
      .put("first_name", "Another")
      .put("last_name", "Chandler")
      .put("email", "chandler.bing@neviswealth.com") // Same email
      .put("description", "Trying to impersonate Chandler");

    vertx.eventBus().<JsonObject>request("clients.create", duplicateClient)
      .onComplete(ctx.failing(err -> ctx.verify(() -> {
        assertThat(err).isInstanceOf(ReplyException.class);
        var replyException = (ReplyException) err;
        assertThat(replyException.failureCode()).isEqualTo(409);
        assertThat(replyException.getMessage()).isEqualTo("Email is already in use");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(3)
  @DisplayName("getClient: must return client when found")
  void getClient_success(Vertx vertx, VertxTestContext ctx) {
    var request = new JsonObject().put("clientId", createdClientId);

    vertx.eventBus().<JsonObject>request("clients.get", request)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var client = reply.body();

        assertThat(client.getString("id")).isEqualTo(createdClientId);
        assertThat(client.getString("first_name")).isEqualTo("Chandler");
        assertThat(client.getString("last_name")).isEqualTo("Bing");
        assertThat(client.getString("email")).isEqualTo("chandler.bing@neviswealth.com");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(4)
  @DisplayName("getClient: must return 404 when client not found")
  void getClient_notFound_returns404(Vertx vertx, VertxTestContext ctx) {
    var request = new JsonObject().put("clientId", "00000000-0000-0000-0000-000000000000");

    vertx.eventBus().<JsonObject>request("clients.get", request)
      .onComplete(ctx.failing(err -> ctx.verify(() -> {
        assertThat(err).isInstanceOf(ReplyException.class);
        var replyException = (ReplyException) err;
        assertThat(replyException.failureCode()).isEqualTo(404);
        assertThat(replyException.getMessage()).isEqualTo("Client not found");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(5)
  @DisplayName("createDocument: must create a new document successfully")
  void createDocument_success(Vertx vertx, VertxTestContext ctx) {
    var documentData = new JsonObject()
      .put("client_id", createdClientId)
      .put("title", "Chandler Bing's Utility Bill of Awkwardness")
      .put("content", "This official-looking utility bill details the excessive energy I've wasted " +
        "trying to explain my job to my parents and the high emotional charges from every failed " +
        "relationship since Janice. It also includes a surprise late fee for that one time I " +
        "accidentally proposed, because apparently sarcasm doesn't show up on the meter. " +
        "Could this BE any more expensive?");

    vertx.eventBus().<JsonObject>request("documents.create", documentData)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var document = reply.body();

        assertThat(document.getString("id"))
          .as("Document ID must be present")
          .isNotNull();
        assertThat(document.getString("created_at"))
          .as("created_at must be present")
          .isNotNull();
        assertThat(document.getString("client_id")).isEqualTo(createdClientId);
        assertThat(document.getString("title")).isEqualTo("Chandler Bing's Utility Bill of Awkwardness");
        assertThat(document.getString("content")).contains("Could this BE any more expensive?");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(6)
  @DisplayName("search: Direct Term Match - 'utility bill' must return document")
  void search_directTermMatch_utilityBill(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "utility bill");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must find at least one result")
          .isNotEmpty();

        var hasDocument = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "document".equals(r.getString("type")) &&
            r.getString("title").contains("Utility Bill"));

        assertThat(hasDocument)
          .as("Must find document with 'Utility Bill' in title")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(7)
  @DisplayName("search: Synonym Match (Thesaurus) - 'address proof' must return document with 'utility bill'")
  void search_synonymMatch_addressProof(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "address proof");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must find results via thesaurus synonym mapping")
          .isNotEmpty();

        var hasUtilityBillDocument = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "document".equals(r.getString("type")) &&
            r.getString("title").contains("Utility Bill"));

        assertThat(hasUtilityBillDocument)
          .as("Thesaurus must map 'address proof' to find document containing 'utility bill'")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(8)
  @DisplayName("search: 'Chandler' must return both client (by first name) and document (by title)")
  void search_byName_returnsBothClientAndDocument(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "Chandler");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must find at least 2 results (client and document)")
          .hasSizeGreaterThanOrEqualTo(2);

        var types = results.stream()
          .map(o -> (JsonObject) o)
          .map(r -> r.getString("type"))
          .collect(Collectors.toSet());

        assertThat(types)
          .as("Must find client by first name 'Chandler'")
          .contains("client");
        assertThat(types)
          .as("Must find document by title containing 'Chandler'")
          .contains("document");

        var clientMatch = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "client".equals(r.getString("type")))
          .findFirst()
          .orElseThrow();
        assertThat(clientMatch.getString("first_name")).isEqualTo("Chandler");

        var documentMatch = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "document".equals(r.getString("type")))
          .findFirst()
          .orElseThrow();
        assertThat(documentMatch.getString("title")).contains("Chandler Bing");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(9)
  @DisplayName("search: 'awkward' must return client (description) and document (content)")
  void search_awkward_returnsBothClientAndDocument(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "awkward");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must find at least 2 results")
          .hasSizeGreaterThanOrEqualTo(2);

        var types = results.stream()
          .map(o -> (JsonObject) o)
          .map(r -> r.getString("type"))
          .collect(Collectors.toSet());

        assertThat(types)
          .as("Must find both client and document")
          .containsExactlyInAnyOrder("client", "document");

        var clientMatch = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "client".equals(r.getString("type")))
          .findFirst()
          .orElseThrow();
        assertThat(clientMatch.getString("description"))
          .as("Client description must contain 'awkward'")
          .contains("awkward");

        var documentMatch = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "document".equals(r.getString("type")))
          .findFirst()
          .orElseThrow();
        assertThat(documentMatch.getString("title"))
          .as("Document title must contain 'Awkwardness'")
          .contains("Awkwardness");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(10)
  @DisplayName("search: 'neviswealth' must return client by email domain")
  void search_byEmailDomain_returnsClient(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "neviswealth");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must find client by email domain")
          .isNotEmpty();

        var clientResult = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "client".equals(r.getString("type")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Must find client by email domain"));

        assertThat(clientResult.getString("email")).isEqualTo("chandler.bing@neviswealth.com");
        assertThat(clientResult.getString("first_name")).isEqualTo("Chandler");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(11)
  @DisplayName("search: must return empty results for non-matching query")
  void search_noMatch_returnsEmpty(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "xyznonexistent123");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();
        assertThat(results)
          .as("Must return empty results for non-matching query")
          .isEmpty();
        ctx.completeNow();
      })));
  }

  @Test
  @Order(12)
  @DisplayName("search: results must be sorted by rank in descending order")
  void search_resultsAreSortedByRank(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "Chandler");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        assertThat(results)
          .as("Must have multiple results to verify sorting")
          .hasSizeGreaterThanOrEqualTo(2);

        var ranks = results.stream()
          .map(o -> (JsonObject) o)
          .map(r -> r.getDouble("rank"))
          .toList();

        assertThat(ranks)
          .as("Results must be sorted by rank descending")
          .isSortedAccordingTo((a, b) -> Double.compare(b, a));

        ctx.completeNow();
      })));
  }

  @Test
  @Order(13)
  @DisplayName("search: each result must contain type and rank fields")
  void search_resultsContainTypeAndRank(Vertx vertx, VertxTestContext ctx) {
    var query = new JsonObject().put("query", "Chandler");

    vertx.eventBus().<JsonArray>request("search", query)
      .onComplete(ctx.succeeding(reply -> ctx.verify(() -> {
        var results = reply.body();

        for (var i = 0; i < results.size(); i++) {
          var result = results.getJsonObject(i);
          assertThat(result.getString("type"))
            .as("Each result must have a type")
            .isNotNull()
            .isIn("client", "document");
          assertThat(result.getDouble("rank"))
            .as("Each result must have a rank")
            .isNotNull();
        }

        ctx.completeNow();
      })));
  }
}

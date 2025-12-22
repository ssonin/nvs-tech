package ssonin.nvstech.api;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
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
import ssonin.nvstech.repository.RepositoryVerticle;

import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(VertxExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiVerticleTest {

  private static final String THESAURUS_CONTENT = """
      address proof : address_proof
      utility bill  : address_proof
      """;

  private static final String THESAURUS_PATH = "/usr/local/share/postgresql/tsearch_data/custom_thesaurus.ths";

  private static final int HTTP_PORT = 18888;
  private static final String API_V1 = "/api/v1";

  @Container
  private static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("nvs_tech_test")
      .withUsername("test_user")
      .withPassword("test_password");

  private WebClient webClient;
  private String createdClientId;
  private String createdDocumentId;

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

    var config = new JsonObject()
      .put("db", dbConfig)
      .put("http.port", HTTP_PORT);

    var options = new DeploymentOptions().setConfig(config);

    var clientOptions = new WebClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(HTTP_PORT);

    webClient = WebClient.create(vertx, clientOptions);

    vertx.deployVerticle(new RepositoryVerticle(), options)
      .compose(__ -> vertx.deployVerticle(new ApiVerticle(), options))
      .onComplete(ctx.succeedingThenComplete());
  }

  @AfterAll
  void tearDown() {
    if (webClient != null) {
      webClient.close();
    }
  }

  @Test
  @Order(1)
  @DisplayName("POST /clients: must create client with all fields and return 201")
  void createClient_withAllFields_returns201(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Monica")
      .put("last_name", "Geller")
      .put("email", "monica.geller@neviswealth.com")
      .put("description", "Chef with an obsessive need for cleanliness and organization.");

    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 201 Created")
          .isEqualTo(201);

        assertThat(response.getHeader("Content-Type"))
          .as("Content-Type must be application/json")
          .isEqualTo("application/json");

        assertThat(response.getHeader("Location"))
          .as("Location header must be present")
          .isNotNull();

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("id"))
          .as("Response must contain client ID")
          .isNotNull();
        assertThat(body.getString("created_at"))
          .as("Response must contain created_at timestamp")
          .isNotNull();
        assertThat(body.getString("first_name"))
          .isEqualTo("Monica");
        assertThat(body.getString("last_name"))
          .isEqualTo("Geller");
        assertThat(body.getString("email"))
          .isEqualTo("monica.geller@neviswealth.com");
        assertThat(body.getString("description"))
          .contains("Chef");

        createdClientId = body.getString("id");

        assertThat(response.getHeader("Location"))
          .as("Location header must contain client ID")
          .endsWith("/" + createdClientId);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(2)
  @DisplayName("POST /clients: must create client without optional description")
  void createClient_withoutDescription_returns201(VertxTestContext ctx) {
    var clientData = new JsonObject()
      .put("first_name", "Ross")
      .put("last_name", "Geller")
      .put("email", "ross.geller@neviswealth.com");

    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(clientData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 201 Created")
          .isEqualTo(201);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("first_name")).isEqualTo("Ross");
        assertThat(body.getString("description"))
          .as("Description must be null when not provided")
          .isNull();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(3)
  @DisplayName("POST /clients: must return 409 when email already exists")
  void createClient_duplicateEmail_returns409(VertxTestContext ctx) {
    var duplicateClient = new JsonObject()
      .put("first_name", "Fake")
      .put("last_name", "Monica")
      .put("email", "monica.geller@neviswealth.com");

    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(duplicateClient)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 409 Conflict")
          .isEqualTo(409);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("error"))
          .as("Error message must indicate email conflict")
          .isEqualTo("Email is already in use");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(4)
  @DisplayName("POST /clients: must return 400 when first_name is empty")
  void createClient_emptyFirstName_returns400(VertxTestContext ctx) {
    var invalidClient = new JsonObject()
      .put("first_name", "")
      .put("last_name", "Geller")
      .put("email", "test@example.com");

    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(invalidClient)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(5)
  @DisplayName("POST /clients: must return 400 when email format is invalid")
  void createClient_invalidEmailFormat_returns400(VertxTestContext ctx) {
    var invalidClient = new JsonObject()
      .put("first_name", "Test")
      .put("last_name", "User")
      .put("email", "not-a-valid-email");

    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(invalidClient)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(6)
  @DisplayName("POST /clients: must return 400 when request body is invalid JSON")
  void createClient_invalidJson_returns400(VertxTestContext ctx) {
    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .sendBuffer(Buffer.buffer("{invalid json"))
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(7)
  @DisplayName("POST /clients: must return 400 when request body is empty")
  void createClient_emptyBody_returns400(VertxTestContext ctx) {
    webClient.post(API_V1 + "/clients")
      .putHeader("Content-Type", "application/json")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(20)
  @DisplayName("GET /clients/:id: must return client when found")
  void getClient_existingClient_returns200(VertxTestContext ctx) {
    webClient.get(API_V1 + "/clients/" + createdClientId)
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        assertThat(response.getHeader("Content-Type"))
          .as("Content-Type must be application/json")
          .isEqualTo("application/json");

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("id")).isEqualTo(createdClientId);
        assertThat(body.getString("first_name")).isEqualTo("Monica");
        assertThat(body.getString("last_name")).isEqualTo("Geller");
        assertThat(body.getString("email")).isEqualTo("monica.geller@neviswealth.com");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(21)
  @DisplayName("GET /clients/:id: must return 404 when client not found")
  void getClient_nonExistentClient_returns404(VertxTestContext ctx) {
    var nonExistentId = UUID.randomUUID().toString();

    webClient.get(API_V1 + "/clients/" + nonExistentId)
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 404 Not Found")
          .isEqualTo(404);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("error"))
          .as("Error message must indicate client not found")
          .isEqualTo("Client not found");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(22)
  @DisplayName("GET /clients/:id: must return 400 when client ID is not a valid UUID")
  void getClient_invalidUuid_returns400(VertxTestContext ctx) {
    webClient.get(API_V1 + "/clients/not-a-uuid")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("error"))
          .as("Error message must indicate invalid client ID")
          .containsIgnoringCase("Invalid client ID");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(30)
  @DisplayName("POST /clients/:id/documents: must create document and return 201")
  void createDocument_validData_returns201(VertxTestContext ctx) {
    var documentData = new JsonObject()
      .put("title", "Monica's Recipe Book")
      .put("content", "A comprehensive collection of recipes from the Geller kitchen, " +
        "featuring classics like Thanksgiving turkey and wedding cakes that may or may not have been dropped.");

    webClient.post(API_V1 + "/clients/" + createdClientId + "/documents")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(documentData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 201 Created")
          .isEqualTo(201);

        assertThat(response.getHeader("Content-Type"))
          .as("Content-Type must be application/json")
          .isEqualTo("application/json");

        assertThat(response.getHeader("Location"))
          .as("Location header must be present")
          .isNotNull();

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("id"))
          .as("Response must contain document ID")
          .isNotNull();
        assertThat(body.getString("created_at"))
          .as("Response must contain created_at timestamp")
          .isNotNull();
        assertThat(body.getString("client_id"))
          .as("Document must be associated with correct client")
          .isEqualTo(createdClientId);
        assertThat(body.getString("title"))
          .isEqualTo("Monica's Recipe Book");
        assertThat(body.getString("content"))
          .contains("Geller kitchen");

        createdDocumentId = body.getString("id");

        assertThat(response.getHeader("Location"))
          .as("Location header must contain document ID")
          .endsWith("/" + createdDocumentId);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(31)
  @DisplayName("POST /clients/:id/documents: must create document with utility bill for thesaurus testing")
  void createDocument_utilityBill_returns201(VertxTestContext ctx) {
    var documentData = new JsonObject()
      .put("title", "Monica's Utility Bill")
      .put("content", "Monthly utility bill for apartment 20, which is always suspiciously high " +
        "given how often we all hang out at the coffee shop instead.");

    webClient.post(API_V1 + "/clients/" + createdClientId + "/documents")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(documentData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 201 Created")
          .isEqualTo(201);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(34)
  @DisplayName("POST /clients/:id/documents: must return 400 when title is empty")
  void createDocument_emptyTitle_returns400(VertxTestContext ctx) {
    var invalidDocument = new JsonObject()
      .put("title", "")
      .put("content", "Some content");

    webClient.post(API_V1 + "/clients/" + createdClientId + "/documents")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(invalidDocument)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(35)
  @DisplayName("POST /clients/:id/documents: must return 404 when client not found")
  void createDocument_clientNotFound_returns404(VertxTestContext ctx) {
    var nonExistentClientId = UUID.randomUUID().toString();
    var documentData = new JsonObject()
      .put("title", "Orphaned Document")
      .put("content", "This document has no home");

    webClient.post(API_V1 + "/clients/" + nonExistentClientId + "/documents")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(documentData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 404 Not Found")
          .isEqualTo(404);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("error"))
          .as("Error message must indicate client not found")
          .isEqualTo("Client not found");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(36)
  @DisplayName("POST /clients/:id/documents: must return 400 when client ID is invalid UUID")
  void createDocument_invalidClientUuid_returns400(VertxTestContext ctx) {
    var documentData = new JsonObject()
      .put("title", "Test Document")
      .put("content", "Test content");

    webClient.post(API_V1 + "/clients/not-a-valid-uuid/documents")
      .putHeader("Content-Type", "application/json")
      .sendJsonObject(documentData)
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        var body = response.bodyAsJsonObject();
        assertThat(body.getString("error"))
          .as("Error message must indicate invalid client ID")
          .containsIgnoringCase("Invalid client ID");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(37)
  @DisplayName("POST /clients/:id/documents: must return 400 when request body is empty")
  void createDocument_emptyBody_returns400(VertxTestContext ctx) {
    webClient.post(API_V1 + "/clients/" + createdClientId + "/documents")
      .putHeader("Content-Type", "application/json")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(50)
  @DisplayName("GET /search: must return client by first name")
  void search_byFirstName_returnsClient(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Monica")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        assertThat(response.getHeader("Content-Type"))
          .as("Content-Type must be application/json")
          .isEqualTo("application/json");

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Must find at least one result")
          .isNotEmpty();

        var hasClient = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "client".equals(r.getString("type")) &&
            "Monica".equals(r.getString("first_name")));

        assertThat(hasClient)
          .as("Must find client with first name 'Monica'")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(51)
  @DisplayName("GET /search: must return document by title keyword")
  void search_byDocumentTitle_returnsDocument(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Recipe")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Must find at least one result")
          .isNotEmpty();

        var hasDocument = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "document".equals(r.getString("type")) &&
            r.getString("title").contains("Recipe"));

        assertThat(hasDocument)
          .as("Must find document with 'Recipe' in title")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(52)
  @DisplayName("GET /search: must return both clients and documents for matching query")
  void search_returnsClientsAndDocuments(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Monica")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var results = response.bodyAsJsonArray();

        assertThat(results)
          .as("Must find multiple results")
          .hasSizeGreaterThanOrEqualTo(2);

        var types = results.stream()
          .map(o -> (JsonObject) o)
          .map(r -> r.getString("type"))
          .collect(Collectors.toSet());

        assertThat(types)
          .as("Must find both client and document types")
          .containsExactlyInAnyOrder("client", "document");

        ctx.completeNow();
      })));
  }

  @Test
  @Order(53)
  @DisplayName("GET /search: must return results sorted by rank descending")
  void search_resultsSortedByRank(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Monica")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var results = response.bodyAsJsonArray();

        assertThat(results)
          .as("Must have multiple results to verify sorting")
          .hasSizeGreaterThanOrEqualTo(2);

        var ranks = results.stream()
          .map(o -> (JsonObject) o)
          .map(r -> r.getDouble("rank"))
          .toList();

        assertThat(ranks)
          .as("Results must be sorted by rank in descending order")
          .isSortedAccordingTo((a, b) -> Double.compare(b, a));

        ctx.completeNow();
      })));
  }

  @Test
  @Order(54)
  @DisplayName("GET /search: each result must contain type and rank fields")
  void search_resultsContainRequiredFields(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Monica")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var results = response.bodyAsJsonArray();

        for (var i = 0; i < results.size(); i++) {
          var result = results.getJsonObject(i);

          assertThat(result.getString("type"))
            .as("Each result must have a type field")
            .isNotNull()
            .isIn("client", "document");

          assertThat(result.getDouble("rank"))
            .as("Each result must have a rank field")
            .isNotNull();

          assertThat(result.getString("id"))
            .as("Each result must have an id field")
            .isNotNull();

          assertThat(result.getString("created_at"))
            .as("Each result must have a created_at field")
            .isNotNull();
        }

        ctx.completeNow();
      })));
  }

  @Test
  @Order(55)
  @DisplayName("GET /search: Synonym Match - 'address proof' must find document with 'utility bill' via thesaurus")
  void search_thesaurusSynonym_findsDocument(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "address proof")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Thesaurus must map 'address proof' to find results")
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
  @Order(56)
  @DisplayName("GET /search: must return client by email domain")
  void search_byEmailDomain_returnsClient(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "neviswealth")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Must find results by email domain")
          .isNotEmpty();

        var hasClient = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "client".equals(r.getString("type")) &&
            r.getString("email").contains("neviswealth"));

        assertThat(hasClient)
          .as("Must find client by email domain")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(57)
  @DisplayName("GET /search: must return empty array for non-matching query")
  void search_noMatch_returnsEmptyArray(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "xyznonexistent123456")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Must return empty array for non-matching query")
          .isEmpty();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(58)
  @DisplayName("GET /search: must return 400 when query parameter is missing")
  void search_missingQueryParam_returns400(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(59)
  @DisplayName("GET /search: must return 400 when query parameter is empty")
  void search_emptyQueryParam_returns400(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(60)
  @DisplayName("GET /search: must return 400 when query parameter is only whitespace")
  void search_whitespaceQueryParam_returns400(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "   ")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 400 Bad Request")
          .isEqualTo(400);

        ctx.completeNow();
      })));
  }

  @Test
  @Order(61)
  @DisplayName("GET /search: query must be case-insensitive")
  void search_caseInsensitive_returnsResults(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "MONICA")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        assertThat(response.statusCode())
          .as("Status code must be 200 OK")
          .isEqualTo(200);

        var results = response.bodyAsJsonArray();
        assertThat(results)
          .as("Case-insensitive search must find results")
          .isNotEmpty();

        var hasClient = results.stream()
          .map(o -> (JsonObject) o)
          .anyMatch(r -> "client".equals(r.getString("type")) &&
            "Monica".equals(r.getString("first_name")));

        assertThat(hasClient)
          .as("Must find client 'Monica' with uppercase query 'MONICA'")
          .isTrue();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(62)
  @DisplayName("GET /search: client results must contain all expected fields")
  void search_clientResult_containsAllFields(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Monica")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var results = response.bodyAsJsonArray();

        var clientResult = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "client".equals(r.getString("type")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Must find a client result"));

        assertThat(clientResult.getString("id")).as("id must be present").isNotNull();
        assertThat(clientResult.getString("created_at")).as("created_at must be present").isNotNull();
        assertThat(clientResult.getString("first_name")).as("first_name must be present").isNotNull();
        assertThat(clientResult.getString("last_name")).as("last_name must be present").isNotNull();
        assertThat(clientResult.getString("email")).as("email must be present").isNotNull();
        assertThat(clientResult.getString("type")).as("type must be 'client'").isEqualTo("client");
        assertThat(clientResult.getDouble("rank")).as("rank must be present").isNotNull();

        ctx.completeNow();
      })));
  }

  @Test
  @Order(63)
  @DisplayName("GET /search: document results must contain all expected fields")
  void search_documentResult_containsAllFields(VertxTestContext ctx) {
    webClient.get(API_V1 + "/search")
      .addQueryParam("q", "Recipe")
      .send()
      .onComplete(ctx.succeeding(response -> ctx.verify(() -> {
        var results = response.bodyAsJsonArray();

        var documentResult = results.stream()
          .map(o -> (JsonObject) o)
          .filter(r -> "document".equals(r.getString("type")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Must find a document result"));

        assertThat(documentResult.getString("id")).as("id must be present").isNotNull();
        assertThat(documentResult.getString("created_at")).as("created_at must be present").isNotNull();
        assertThat(documentResult.getString("client_id")).as("client_id must be present").isNotNull();
        assertThat(documentResult.getString("title")).as("title must be present").isNotNull();
        assertThat(documentResult.getString("content")).as("content must be present").isNotNull();
        assertThat(documentResult.getString("type")).as("type must be 'document'").isEqualTo("document");
        assertThat(documentResult.getDouble("rank")).as("rank must be present").isNotNull();

        ctx.completeNow();
      })));
  }
}

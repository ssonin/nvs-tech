package ssonin.searchapi;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusIntegrationTest
class BootIT {

  @Test
  void returns_not_found_for_undefined_root() {
    given()
      .when().get("/")
      .then()
      .statusCode(404);
  }
}

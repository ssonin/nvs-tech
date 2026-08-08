package ssonin.searchapi;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class BootTest {

  @Test
  void test() {
    given()
      .when().get("/")
      .then()
      .statusCode(404);
  }
}

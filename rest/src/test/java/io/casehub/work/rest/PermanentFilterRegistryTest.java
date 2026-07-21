package io.casehub.work.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class PermanentFilterRegistryTest {

    @Test
    void listRules_includesPermanentWithSource() {
        given().get("/label-rules")
               .then().statusCode(200)
               .body("findAll { it.source == 'permanent' }.name", hasItem("test/apply-label"));
    }
}

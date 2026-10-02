package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestTransaction
class CreateEndpointSmokeTest {

    @Test
    void createWorkItem_returns201WithId() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "title": "Smoke test item",
                    "description": "Validates APT endpoint works",
                    "priority": "MEDIUM",
                    "createdBy": "system"
                }
                """)
            .when().post("/api/work/items/create")
            .then()
            .statusCode(201)
            .body("id", notNullValue());
    }
}

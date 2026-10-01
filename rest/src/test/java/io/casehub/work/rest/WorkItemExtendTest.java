package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.casehub.work.rest.test.WorkItemTestFixture;
import io.restassured.http.ContentType;

/**
 * Integration tests for PUT /workitems/{id}/extend.
 *
 * <p>
 * extend() moves the expiresAt deadline forward. It requires:
 * - newExpiresAt after current expiresAt (400 otherwise)
 * - non-terminal status (409 if terminal)
 *
 * <p>
 * Needed by BreachDecision.Extend in the SLA breach executor. Refs #211.
 */
@QuarkusTest
class WorkItemExtendTest {

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Extend test\",\"createdBy\":\"test\",\"candidateGroups\":\"ops\"}")
                .post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void extend_happyPath_returns200_andExpiresAtIsUpdated() {
        final String id = createWorkItem();
        // Truncate to seconds — avoids nanosecond format divergence between Instant.toString() and Jackson
        final String newExpiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();

        given().contentType(ContentType.JSON)
                .body("{\"newExpiresAt\":\"" + newExpiry + "\"}")
                .post("/api/work/lifecycle/extend/" + id + "?actor=admin")
                .then()
                .statusCode(200)
                .body("expiresAt", equalTo(newExpiry));
    }

    @Test
    void extend_newExpiresAtNotAfterCurrent_returns500() {
        final String id = createWorkItem();
        // Use a past instant — guaranteed to be before current expiresAt
        final String pastExpiry = Instant.now().minus(1, ChronoUnit.DAYS).toString();

        given().contentType(ContentType.JSON)
                .body("{\"newExpiresAt\":\"" + pastExpiry + "\"}")
                .post("/api/work/lifecycle/extend/" + id + "?actor=admin")
                .then()
                .statusCode(400);
    }

    @Test
    void extend_terminalItem_returns409() {
        final String id = createWorkItem();
        // Cancel the item to put it into a terminal status
        given().contentType(ContentType.JSON).body("{}").post("/api/work/lifecycle/cancel/" + id + "?actor=admin").then().statusCode(200);

        // Truncate to seconds — avoids nanosecond format divergence between Instant.toString() and Jackson
        final String newExpiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        given().contentType(ContentType.JSON)
                .body("{\"newExpiresAt\":\"" + newExpiry + "\"}")
                .post("/api/work/lifecycle/extend/" + id + "?actor=admin")
                .then()
                .statusCode(409);
    }

    @Test
    void extend_unknownId_returns404() {
        // Truncate to seconds — avoids nanosecond format divergence between Instant.toString() and Jackson
        final String newExpiry = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
        given().contentType(ContentType.JSON)
                .body("{\"newExpiresAt\":\"" + newExpiry + "\"}")
                .post("/api/work/lifecycle/extend/" + UUID.randomUUID() + "?actor=admin")
                .then()
                .statusCode(404);
    }
}

package io.casehub.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueHealthReportTest {

    @Test
    void report_returns200_withExpectedStructure() {
        given().get("/workitems/reports/queue-health")
                .then().statusCode(200)
                .body("timestamp", notNullValue())
                .body("overdueCount", notNullValue())
                .body("pendingCount", notNullValue())
                .body("avgPendingAgeSeconds", notNullValue())
                .body("criticalOverdueCount", notNullValue());
    }

    @Test
    void pendingCount_includesPendingItems() {
        final String type = "pending-" + System.nanoTime();
        createItem(type, null, null);
        createItem(type, null, null);

        final int count = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("pendingCount");
        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    void pendingCount_excludesCompletedItems() {
        final String type = "pend-excl-" + System.nanoTime();
        final String id = createItem(type, null, null);
        given().put("/workitems/" + id + "/claim?claimant=actor").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=actor").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=actor").then().statusCode(200);

        final int count = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("pendingCount");
        assertThat(count).isZero();
    }

    @Test
    void overdueCount_includesActiveItemsPastExpiry() {
        final String type = "overdue-" + System.nanoTime();
        createItem(type, Instant.now().minus(5, ChronoUnit.MINUTES).toString(), null);

        final int overdue = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(overdue).isGreaterThanOrEqualTo(1);
    }

    @Test
    void overdueCount_excludesCompletedItems() {
        final String type = "overdue-compl-" + System.nanoTime();
        final String id = createItem(type, Instant.now().minus(5, ChronoUnit.MINUTES).toString(), null);
        given().put("/workitems/" + id + "/claim?claimant=a").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=a").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=a").then().statusCode(200);

        final int overdue = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(overdue).isZero();
    }

    @Test
    void overdueCount_excludesFutureExpiry() {
        final String type = "not-overdue-" + System.nanoTime();
        createItem(type, Instant.now().plus(1, ChronoUnit.HOURS).toString(), null);

        final int overdue = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(overdue).isZero();
    }

    @Test
    void criticalOverdueCount_isSubsetOfOverdueCount() {
        final String type = "crit-" + System.nanoTime();
        final String past = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        createItem(type, past, "URGENT");
        createItem(type, past, "MEDIUM");

        final var resp = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().response();

        final int overdue = resp.path("overdueCount");
        final int critOverdue = resp.path("criticalOverdueCount");
        assertThat(critOverdue).isGreaterThanOrEqualTo(1);
        assertThat(critOverdue).isLessThanOrEqualTo(overdue);
    }

    @Test
    void avgPendingAgeSeconds_isNonNegative() {
        final String type = "age-" + System.nanoTime();
        createItem(type, null, null);

        final int age = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("avgPendingAgeSeconds");
        assertThat(age).isGreaterThanOrEqualTo(0);
    }

    @Test
    void oldestUnclaimedCreatedAt_isNullWhenNoPendingItems() {
        given().queryParam("type", "empty-queue-" + System.nanoTime())
                .get("/workitems/reports/queue-health")
                .then().statusCode(200)
                .body("oldestUnclaimedCreatedAt", nullValue());
    }

    @Test
    void oldestUnclaimedCreatedAt_isPresentWhenPendingItemsExist() {
        final String type = "oldest-" + System.nanoTime();
        createItem(type, null, null);

        given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200)
                .body("oldestUnclaimedCreatedAt", notNullValue());
    }

    @Test
    void filterByType_scopesAllCounts() {
        final String typeA = "qa-" + System.nanoTime();
        final String typeB = "qb-" + System.nanoTime();
        final String past = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        createItem(typeA, past, null);
        createItem(typeB, past, null);

        final int overdueA = given().queryParam("type", typeA)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(overdueA).isGreaterThanOrEqualTo(1);

        final int overdueB = given().queryParam("type", typeB)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(overdueB).isGreaterThanOrEqualTo(1);
    }

    @Test
    void filterByPriority_scopesOverdueToPriority() {
        final String type = "prio-qh-" + System.nanoTime();
        final String past = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        createItem(type, past, "HIGH");
        createItem(type, past, "LOW");

        final int highOverdue = given().queryParam("type", type).queryParam("priority", "HIGH")
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().path("overdueCount");
        assertThat(highOverdue).isEqualTo(1);
    }

    @Test
    void invalidPriority_returns400() {
        given().queryParam("priority", "ULTRA")
                .get("/workitems/reports/queue-health")
                .then().statusCode(400);
    }

    @Test
    void allItemsCompleted_allCountsZero() {
        final String type = "allcompl-" + System.nanoTime();
        final String id = createItem(type, null, null);
        given().put("/workitems/" + id + "/claim?claimant=a").then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=a").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=a").then().statusCode(200);

        final var resp = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().response();

        assertThat((Integer) resp.path("pendingCount")).isZero();
        assertThat((Integer) resp.path("overdueCount")).isZero();
        assertThat((Integer) resp.path("criticalOverdueCount")).isZero();
        assertThat((Object) resp.path("oldestUnclaimedCreatedAt")).isNull();
    }

    @Test
    void e2e_mixedQueue_allFieldsCorrect() {
        final String type = "e2e-qh-" + System.nanoTime();
        final String past = Instant.now().minus(5, ChronoUnit.MINUTES).toString();

        createItem(type, past, "URGENT"); // overdue + critical overdue
        createItem(type, null, null); // pending, not overdue

        final String doneId = createItem(type, null, null);
        given().put("/workitems/" + doneId + "/claim?claimant=a").then().statusCode(200);
        given().put("/workitems/" + doneId + "/start?actor=a").then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + doneId + "/complete?actor=a").then().statusCode(200);

        final var resp = given().queryParam("type", type)
                .get("/workitems/reports/queue-health")
                .then().statusCode(200).extract().response();

        assertThat((Integer) resp.path("pendingCount")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("overdueCount")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) resp.path("criticalOverdueCount")).isGreaterThanOrEqualTo(1);
        assertThat((Object) resp.path("oldestUnclaimedCreatedAt")).isNotNull();
        assertThat((Integer) resp.path("avgPendingAgeSeconds")).isGreaterThanOrEqualTo(0);
    }

    private String createItem(final String type, final String expiresAt, final String priority) {
        final StringBuilder body = new StringBuilder("{\"title\":\"QH Test\",\"types\":[\"")
                .append(type).append("\"],\"createdBy\":\"test\"");
        if (expiresAt != null) {
            body.append(",\"expiresAt\":\"").append(expiresAt).append("\"");
        }
        if (priority != null) {
            body.append(",\"priority\":\"").append(priority).append("\"");
        }
        body.append("}");
        return given().contentType(ContentType.JSON).body(body.toString())
                .post("/workitems").then().statusCode(201).extract().path("id");
    }
}

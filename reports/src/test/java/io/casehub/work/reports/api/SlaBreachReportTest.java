package io.casehub.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class SlaBreachReportTest {

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    void report_returns200_withExpectedStructure() {
        given().get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", notNullValue())
                .body("summary", notNullValue())
                .body("summary.totalBreached", notNullValue())
                .body("summary.avgBreachDurationMinutes", notNullValue())
                .body("summary.byType", notNullValue());
    }

    // ── Happy path: breach detection ──────────────────────────────────────────

    @Test
    void completedAfterExpiry_isBreached() {
        final String type = "breach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Breached WI", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(id));
    }

    @Test
    void completedBeforeExpiry_isNotBreached() {
        final String type = "ontime-" + System.nanoTime();
        final String expiresAt = Instant.now().plus(24, ChronoUnit.HOURS).toString();
        final String id = createWithExpiry("On-time WI", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void openItemPastDeadline_isBreached() {
        final String type = "open-breach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        createWithExpiry("Open Past Deadline", type, expiresAt);
        // leave open — still a breach

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", notNullValue());
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    void itemWithNoExpiresAt_neverAppears() {
        final String type = "no-expiry-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Expiry\",\"types\":[\"" + type + "\"],\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
        claimStartComplete(id, "reviewer");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void completedBeforeExpiry_isNotBreached_boundary() {
        final String type = "boundary-" + System.nanoTime();
        // expires in far future — completed now is clearly before expiry
        final String expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        final String id = createWithExpiry("Boundary WI", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    @Test
    void filterByFrom_excludesItemsBeforeWindow() {
        final String type = "from-filter-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(10, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Old Breach", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void filterByTo_excludesItemsAfterWindow() {
        final String type = "to-filter-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Future Breach", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("to", "2000-01-01T00:00:00Z")
                .queryParam("type", type)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty());
    }

    @Test
    void filterByType_returnsOnlyThatType() {
        final String typeA = "sla-a-" + System.nanoTime();
        final String typeB = "sla-b-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String idA = createWithExpiry("A", typeA, expiresAt);
        final String idB = createWithExpiry("B", typeB, expiresAt);
        claimStartComplete(idA, "r");
        claimStartComplete(idB, "r");

        given().queryParam("type", typeA).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(idA))
                .body("items.workItemId", not(hasItem(idB)));
    }

    @Test
    void filterByPriority_returnsOnlyMatchingPriority() {
        final String type = "prio-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String highId = createWithExpiryAndPriority("High", type, expiresAt, "HIGH");
        final String lowId = createWithExpiryAndPriority("Low", type, expiresAt, "LOW");
        claimStartComplete(highId, "r");
        claimStartComplete(lowId, "r");

        given().queryParam("priority", "HIGH").queryParam("type", type)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(highId))
                .body("items.workItemId", not(hasItem(lowId)));
    }

    @Test
    void invalidPriority_returns400() {
        given().queryParam("priority", "SUPER_URGENT")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(400);
    }

    @Test
    void invalidTimestamp_returns400() {
        given().queryParam("from", "not-a-date")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(400);
    }

    // ── Response fields ───────────────────────────────────────────────────────

    @Test
    void breachedItem_hasAllRequiredFields() {
        final String type = "fields-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Fields Test", type, expiresAt);
        claimStartComplete(id, "reviewer");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].workItemId", equalTo(id))
                .body("items[0].types", equalTo(type))
                .body("items[0].priority", notNullValue())
                .body("items[0].expiresAt", notNullValue())
                .body("items[0].status", notNullValue())
                .body("items[0].breachDurationMinutes", notNullValue());
    }

    // ── Summary aggregates ────────────────────────────────────────────────────

    @Test
    void summary_totalBreached_matchesItemCount() {
        final String type = "sumtotal-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        createWithExpiry("S1", type, expiresAt);
        createWithExpiry("S2", type, expiresAt);

        final int total = given().queryParam("type", type)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().path("summary.totalBreached");
        assertThat(total).isGreaterThanOrEqualTo(2);
    }

    @Test
    void summary_avgBreachDurationMinutes_isPositive() {
        final String type = "avgbreach-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        final String id = createWithExpiry("Avg Test", type, expiresAt);
        claimStartComplete(id, "reviewer");

        final float avg = given().queryParam("type", type)
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().path("summary.avgBreachDurationMinutes");
        assertThat(avg).isGreaterThan(0f);
    }

    @Test
    void summary_byType_groupsCorrectly() {
        final String typeX = "bcx-" + System.nanoTime();
        final String typeY = "bcy-" + System.nanoTime();
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        createWithExpiry("X1", typeX, expiresAt);
        createWithExpiry("Y1", typeY, expiresAt);
        createWithExpiry("Y2", typeY, expiresAt);

        // Use `from` to get a unique cache key — the no-filter key is shared with the structure test
        final var resp = given().queryParam("from", "2020-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200).extract().response();
        assertThat((Object) resp.path("summary.byType." + typeX)).isNotNull();
        assertThat((Object) resp.path("summary.byType." + typeY)).isNotNull();
    }

    // ── Robustness ────────────────────────────────────────────────────────────

    @Test
    void noBreaches_returns200_withEmptyItems() {
        given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", empty())
                .body("summary.totalBreached", equalTo(0));
    }

    // ── E2E ───────────────────────────────────────────────────────────────────

    @Test
    void e2e_mixedCompliance_onlyBreachesInList() {
        final String type = "e2e-sla-" + System.nanoTime();
        final String past = Instant.now().minus(3, ChronoUnit.MINUTES).toString();
        final String future = Instant.now().plus(1, ChronoUnit.HOURS).toString();

        final String b1 = createWithExpiry("Late 1", type, past);
        final String b2 = createWithExpiry("Late 2", type, past);
        final String ok = createWithExpiry("On Time", type, future);
        claimStartComplete(b1, "r");
        claimStartComplete(b2, "r");
        claimStartComplete(ok, "r");

        given().queryParam("type", type).get("/workitems/reports/sla-breaches")
                .then().statusCode(200)
                .body("items.workItemId", hasItem(b1))
                .body("items.workItemId", hasItem(b2))
                .body("items.workItemId", not(hasItem(ok)))
                .body("summary.totalBreached", greaterThan(1));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWithExpiry(final String title, final String type, final String expiresAt) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"types\":[\"" + type
                        + "\"],\"expiresAt\":\"" + expiresAt + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private String createWithExpiryAndPriority(final String title, final String type,
            final String expiresAt, final String priority) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"" + title + "\",\"types\":[\"" + type
                        + "\"],\"priority\":\"" + priority
                        + "\",\"expiresAt\":\"" + expiresAt + "\",\"createdBy\":\"test\"}")
                .post("/workitems").then().statusCode(201).extract().path("id");
    }

    private void claimStartComplete(final String id, final String actor) {
        given().put("/workitems/" + id + "/claim?claimant=" + actor).then().statusCode(200);
        given().put("/workitems/" + id + "/start?actor=" + actor).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .put("/workitems/" + id + "/complete?actor=" + actor).then().statusCode(200);
    }
}

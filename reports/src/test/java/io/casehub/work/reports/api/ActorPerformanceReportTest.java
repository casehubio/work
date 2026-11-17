package io.casehub.work.reports.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.casehub.work.reports.test.MutableCurrentPrincipal;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class ActorPerformanceReportTest {

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    @Test
    void report_returns200_withExpectedStructure() {
        final String actor = "struct-" + System.nanoTime();
        given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200)
                .body("actorId", equalTo(actor))
                .body("totalAssigned", notNullValue())
                .body("totalCompleted", notNullValue())
                .body("totalRejected", notNullValue())
                .body("byType", notNullValue());
    }

    @Test
    void unknownActor_returns200_withZeroCounts() {
        final String actor = "nobody-" + System.nanoTime();
        given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200)
                .body("totalAssigned", equalTo(0))
                .body("totalCompleted", equalTo(0))
                .body("totalRejected", equalTo(0));
    }

    @Test
    void totalAssigned_incrementsPerClaim() {
        final String actor = "assigned-" + System.nanoTime();
        final String id1 = createWorkItem("cat1");
        final String id2 = createWorkItem("cat1");
        principal.setActorId(actor);
        given().post("/api/work/lifecycle/claim/" + id1).then().statusCode(200);
        given().post("/api/work/lifecycle/claim/" + id2).then().statusCode(200);

        final int assigned = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalAssigned");
        assertThat(assigned).isGreaterThanOrEqualTo(2);
    }

    @Test
    void totalCompleted_incrementsPerCompletion() {
        final String actor = "compl-" + System.nanoTime();
        claimStartComplete(createWorkItem("cc"), actor);
        claimStartComplete(createWorkItem("cc"), actor);

        final int completed = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isGreaterThanOrEqualTo(2);
    }

    @Test
    void totalRejected_incrementsPerRejection() {
        final String actor = "rej-" + System.nanoTime();
        final String id = createWorkItem("rc");
        principal.setActorId(actor);
        given().post("/api/work/lifecycle/claim/" + id).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"reason\":\"blocked\"}")
                .post("/api/work/lifecycle/reject/" + id).then().statusCode(200);

        final int rejected = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalRejected");
        assertThat(rejected).isGreaterThanOrEqualTo(1);
    }

    @Test
    void avgCompletionMinutes_isNull_forActorWithNoCompletions() {
        final String actor = "nocomp-" + System.nanoTime();
        principal.setActorId(actor);
        given().post("/api/work/lifecycle/claim/" + createWorkItem("nc")).then().statusCode(200);

        final Object avg = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");
        assertThat(avg).isNull();
    }

    @Test
    void avgCompletionMinutes_isNonNegative_whenCompletionsExist() {
        final String actor = "avgactor-" + System.nanoTime();
        claimStartComplete(createWorkItem("ac"), actor);

        final float avg = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("avgCompletionMinutes");
        assertThat(avg).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void byType_countsCompletedPerType() {
        final String actor = "bytype-" + System.nanoTime();
        final String typeA = "bca-" + System.nanoTime();
        final String typeB = "bcb-" + System.nanoTime();
        claimStartComplete(createWorkItem(typeA), actor);
        claimStartComplete(createWorkItem(typeA), actor);
        claimStartComplete(createWorkItem(typeB), actor);

        final var resp = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().response();
        assertThat((Integer) resp.path("byType." + typeA)).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("byType." + typeB)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void filterByFrom_farFuture_returnsZeroCounts() {
        final String actor = "fromfilt-" + System.nanoTime();
        claimStartComplete(createWorkItem("fc"), actor);

        final int completed = given().queryParam("from", "2099-01-01T00:00:00Z")
                .get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isZero();
    }

    @Test
    void filterByTo_farPast_returnsZeroCounts() {
        final String actor = "tofilt-" + System.nanoTime();
        claimStartComplete(createWorkItem("tc"), actor);

        final int completed = given().queryParam("to", "2000-01-01T00:00:00Z")
                .get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(completed).isZero();
    }

    @Test
    void filterByType_scopesCountsToThatType() {
        final String actor = "typescope-" + System.nanoTime();
        final String typeA = "cs-a-" + System.nanoTime();
        final String typeB = "cs-b-" + System.nanoTime();
        claimStartComplete(createWorkItem(typeA), actor);
        claimStartComplete(createWorkItem(typeB), actor);

        final int inA = given().queryParam("type", typeA)
                .get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().path("totalCompleted");
        assertThat(inA).isEqualTo(1);
    }

    @Test
    void e2e_fullLifecycle_allCountsCorrect() {
        final String actor = "e2eactor-" + System.nanoTime();
        final String type = "e2etype-" + System.nanoTime();

        claimStartComplete(createWorkItem(type), actor);
        claimStartComplete(createWorkItem(type), actor);

        final String rejId = createWorkItem(type);
        principal.setActorId(actor);
        given().post("/api/work/lifecycle/claim/" + rejId).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + rejId).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{\"reason\":\"blocked\"}")
                .post("/api/work/lifecycle/reject/" + rejId).then().statusCode(200);

        final String inFlightId = createWorkItem(type);
        given().post("/api/work/lifecycle/claim/" + inFlightId).then().statusCode(200);

        final var resp = given().get("/api/work/reports/actor-performance/" + actor)
                .then().statusCode(200).extract().response();
        assertThat((Integer) resp.path("totalCompleted")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) resp.path("totalRejected")).isGreaterThanOrEqualTo(1);
        assertThat((Integer) resp.path("totalAssigned")).isGreaterThanOrEqualTo(4);
        assertThat((Float) resp.path("avgCompletionMinutes")).isGreaterThanOrEqualTo(0f);
        assertThat((Integer) resp.path("byType." + type)).isGreaterThanOrEqualTo(2);
    }

    private String createWorkItem(final String type) {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Perf Test\",\"types\":[\"" + type + "\"],\"createdBy\":\"test\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
    }

    private void claimStartComplete(final String id, final String actor) {
        principal.setActorId(actor);
        given().post("/api/work/lifecycle/claim/" + id).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .post("/api/work/lifecycle/complete/" + id).then().statusCode(200);
    }
}

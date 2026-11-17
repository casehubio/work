package io.casehub.work.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;

/**
 * Black-box integration tests run against the packaged artifact (JVM uber-jar or native binary).
 * These tests exercise the full HTTP stack without access to CDI internals.
 * <p>
 * In JVM mode: {@code mvn verify -pl runtime}
 * In native mode: {@code JAVA_HOME=<graalvm> mvn verify -Pnative -pl runtime}
 */
@QuarkusIntegrationTest
class WorkItemNativeIT {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a minimal WorkItem and returns its id. */
    private String createWorkItem(String title) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "%s",
                          "priority": "MEDIUM",
                          "createdBy": "native-test"
                        }
                        """.formatted(title))
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");
    }

    // -------------------------------------------------------------------------
    // POST / — create
    // -------------------------------------------------------------------------

    @Test
    void create_returns201WithLocationAndPendingStatus() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Native create test",
                          "priority": "HIGH",
                          "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("status", equalTo("PENDING"))
                .body("priority", equalTo("HIGH"))
                .body("createdBy", equalTo("system"))
                .extract().path("id");

        assertThat(id).isNotNull();
    }

    @Test
    void create_appliesDefaultExpiresAt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Default expiry test","priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .body("expiresAt", notNullValue());
    }

    @Test
    void create_withCandidateGroups_storesThem() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Group task native",
                          "candidateGroups": "finance,leads",
                          "priority": "MEDIUM",
                          "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .body("candidateGroups", equalTo("finance,leads"));
    }

    // -------------------------------------------------------------------------
    // GET / and GET /{id}
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsNonNullArray() {
        createWorkItem("List test native");
        given()
                .when().get("/api/work/items/list-all")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void getById_returnsWorkItemWithAuditTrail() {
        String id = createWorkItem("Get by id native");
        given()
                .when().get("/api/work/items/get-by-id/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("PENDING"))
                .body("auditTrail", notNullValue())
                .body("auditTrail.size()", greaterThanOrEqualTo(1))
                .body("auditTrail[0].event", equalTo("CREATED"));
    }

    @Test
    void getById_unknownId_returns404WithErrorBody() {
        given()
                .when().get("/api/work/items/get-by-id/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("error", containsString("not found"));
    }

    // -------------------------------------------------------------------------
    // Full happy path lifecycle
    // -------------------------------------------------------------------------

    @Test
    void fullHappyPath_createClaimStartComplete() {
        String id = createWorkItem("Full lifecycle native");

        given().post("/api/work/lifecycle/claim/" + id)
                .then().statusCode(200).body("status", equalTo("ASSIGNED"));

        given().post("/api/work/lifecycle/start/" + id)
                .then().statusCode(200).body("status", equalTo("IN_PROGRESS"));

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"approved\\\":true}\"}")
                .post("/api/work/lifecycle/complete/" + id)
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("resolution", notNullValue());
    }

    @Test
    void rejectPath_claimThenReject() {
        String id = createWorkItem("Reject native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"out of scope\"}")
                .post("/api/work/lifecycle/reject/" + id)
                .then().statusCode(200).body("status", equalTo("REJECTED"));
    }

    @Test
    void delegatePath_claimThenDelegate() {
        String id = createWorkItem("Delegate native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().contentType(ContentType.JSON)
                .body("{\"to\":\"bob\"}")
                .post("/api/work/lifecycle/delegate/" + id)
                .then().statusCode(200)
                .body("status", equalTo("DELEGATED"))
                .body("assigneeId", equalTo("bob"));
    }

    @Test
    void releasePath_claimThenRelease() {
        String id = createWorkItem("Release native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().post("/api/work/lifecycle/release/" + id)
                .then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    @Test
    void suspendResumePath_fullCycle() {
        String id = createWorkItem("Suspend resume native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().post("/api/work/lifecycle/start/" + id);
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"blocked\"}")
                .post("/api/work/lifecycle/suspend/" + id)
                .then().statusCode(200).body("status", equalTo("SUSPENDED"));

        given().post("/api/work/lifecycle/resume/" + id)
                .then().statusCode(200).body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void cancelPath_cancelFromPending() {
        String id = createWorkItem("Cancel native");
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"no longer needed\"}")
                .post("/api/work/lifecycle/cancel/" + id)
                .then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // Inbox query
    // -------------------------------------------------------------------------

    @Test
    void inbox_noParams_returns200() {
        createWorkItem("Inbox native");
        given()
                .when().get("/api/work/items/inbox")
                .then().statusCode(200);
    }

    @Test
    void inbox_filterByCandidateGroup_findsItem() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Inbox group native",
                          "candidateGroups": "native-team",
                          "priority": "MEDIUM",
                          "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create");

        List<String> ids = given()
                .queryParam("candidateGroup", "native-team")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");

        assertThat(ids).isNotEmpty();
    }

    @Test
    void inbox_filterByStatus_pending() {
        createWorkItem("Inbox status native");
        given()
                .queryParam("status", "PENDING")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200);
    }

    @Test
    void inbox_filterByPriority_high() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"title\":\"High prio native\",\"priority\":\"HIGH\",\"createdBy\":\"system\"}")
                .when().post("/api/work/items/create");

        given()
                .queryParam("priority", "HIGH")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200);
    }

    // -------------------------------------------------------------------------
    // Error cases — validate exception mappers work in native
    // -------------------------------------------------------------------------

    @Test
    void claimAlreadyAssigned_returns409WithErrorBody() {
        String id = createWorkItem("409 native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().post("/api/work/lifecycle/claim/" + id)
                .then()
                .statusCode(409)
                .body("error", notNullValue());
    }

    @Test
    void completePendingItem_returns409() {
        String id = createWorkItem("409 complete native");
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"done\"}")
                .post("/api/work/lifecycle/complete/" + id)
                .then().statusCode(409);
    }

    // -------------------------------------------------------------------------
    // Audit trail integrity in packaged mode
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Report endpoints — smoke tests (quarkus-work-reports module)
    // -------------------------------------------------------------------------

    @Test
    void reports_slaBreaches_returns200() {
        given().get("/api/work/reports/sla-breaches")
                .then().statusCode(200)
                .body("items", notNullValue())
                .body("summary", notNullValue());
    }

    @Test
    void reports_actorPerformance_returns200() {
        given().get("/api/work/reports/actor-performance/smoke-actor")
                .then().statusCode(200)
                .body("actorId", equalTo("smoke-actor"))
                .body("totalCompleted", notNullValue());
    }

    @Test
    void reports_throughput_missingFrom_returns400() {
        given().queryParam("to", "2026-04-27T00:00:00Z")
                .get("/api/work/reports/throughput")
                .then().statusCode(400);
    }

    @Test
    void reports_throughput_returns200() {
        given().queryParam("from", "2026-01-01T00:00:00Z")
                .queryParam("to", "2026-12-31T23:59:59Z")
                .queryParam("groupBy", "month")
                .get("/api/work/reports/throughput")
                .then().statusCode(200)
                .body("buckets", notNullValue());
    }

    @Test
    void reports_queueHealth_returns200() {
        given().get("/api/work/reports/queue-health")
                .then().statusCode(200)
                .body("timestamp", notNullValue())
                .body("overdueCount", notNullValue())
                .body("pendingCount", notNullValue());
    }

    @Test
    void reports_slaBreaches_e2e_breach_appears() {
        final String expiresAt = Instant.now().minus(2, ChronoUnit.MINUTES).toString();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"IT Breach\",\"expiresAt\":\"" + expiresAt + "\",\"createdBy\":\"it\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
        given().post("/api/work/lifecycle/claim/" + id).then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);
        given().contentType(ContentType.JSON).body("{}")
                .post("/api/work/lifecycle/complete/" + id).then().statusCode(200);

        // Use a `from` param to get a distinct cache key — the unfiltered endpoint
        // is cached at production TTL and may return a stale 0 from the smoke test above.
        final String from = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        final int totalBreached = given().queryParam("from", from)
                .get("/api/work/reports/sla-breaches")
                .then().statusCode(200).extract().path("summary.totalBreached");
        assertThat(totalBreached).isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Audit trail integrity in packaged mode
    // -------------------------------------------------------------------------

    @Test
    void auditTrailHasFourEntriesAfterFullPath() {
        String id = createWorkItem("Audit native");
        given().post("/api/work/lifecycle/claim/" + id);
        given().post("/api/work/lifecycle/start/" + id);
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"done\"}")
                .post("/api/work/lifecycle/complete/" + id);

        given()
                .when().get("/api/work/items/get-by-id/" + id)
                .then().statusCode(200)
                .body("auditTrail.size()", equalTo(4))
                .body("auditTrail[0].event", equalTo("CREATED"))
                .body("auditTrail[1].event", equalTo("ASSIGNED"))
                .body("auditTrail[2].event", equalTo("STARTED"))
                .body("auditTrail[3].event", equalTo("COMPLETED"));
    }

    // -------------------------------------------------------------------------
    // Escalate
    // -------------------------------------------------------------------------

    @Test
    void escalate_pendingToNewGroup() {
        String id = createWorkItem("Escalate native");
        given().contentType(ContentType.JSON)
                .body("""
                        {"targetGroup":"senior-team","reason":"complex case"}
                        """)
                .post("/api/work/lifecycle/escalate/" + id)
                .then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("candidateGroups", equalTo("senior-team"))
                .body("assigneeId", nullValue());
    }

    @Test
    void escalate_assignedToNewGroup() {
        String id = createWorkItem("Escalate assigned");
        given().post("/api/work/lifecycle/claim/" + id);
        given().contentType(ContentType.JSON)
                .body("""
                        {"targetGroup":"tier-2","reason":"needs specialist"}
                        """)
                .post("/api/work/lifecycle/escalate/" + id)
                .then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("candidateGroups", equalTo("tier-2"))
                .body("assigneeId", nullValue());
    }

    @Test
    void escalate_terminalItem_returns409() {
        String id = createWorkItem("Escalate terminal");
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"cancelled\"}")
                .post("/api/work/lifecycle/cancel/" + id);
        given().contentType(ContentType.JSON)
                .body("""
                        {"targetGroup":"senior-team","reason":"too late"}
                        """)
                .post("/api/work/lifecycle/escalate/" + id)
                .then().statusCode(409)
                .body("error", containsString("Cannot escalate"));
    }
}

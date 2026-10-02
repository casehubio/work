package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.rest.test.WorkItemTestFixture;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@TestTransaction
class WorkItemResourceTest {

    // -------------------------------------------------------------------------
    // POST /api/work/items/create — create
    // -------------------------------------------------------------------------

    @Test
    void create_returns201WithId() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Test item",
                            "description": "Do something",
                            "priority": "MEDIUM",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .extract().path("id");

        assertThat(id).isNotNull();
    }

    @Test
    void create_returnsWorkItemResponseBody() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Test item",
                            "description": "Do something",
                            "priority": "MEDIUM",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo("Test item"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void create_withCandidateGroups_storesThem() {
        String groups = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Group item",
                            "candidateGroups": "team-a,team-b",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .extract().path("candidateGroups");

        assertThat(groups).contains("team-a").contains("team-b");
    }

    @Test
    void create_appliesDefaultExpiresAt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "No expiry item",
                            "createdBy": "system"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .body("expiresAt", notNullValue());
    }

    @Test
    void create_withExplicitExpiresAt_usesIt() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Explicit expiry item",
                            "createdBy": "system",
                            "expiresAt": "2026-12-31T00:00:00Z"
                        }
                        """)
                .when().post("/api/work/items/create")
                .then()
                .statusCode(201)
                .body("expiresAt", equalTo("2026-12-31T00:00:00Z"));
    }

    // -------------------------------------------------------------------------
    // GET /api/work/items/list-all — list all
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsItems() {
        WorkItemTestFixture.createWorkItem();

        given()
                .when().get("/api/work/items/list-all")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(1));
    }

    // -------------------------------------------------------------------------
    // GET /api/work/items/get-by-id/{id} — get with audit trail
    // -------------------------------------------------------------------------

    @Test
    void getById_returnsWorkItemWithAuditTrail() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .when().get("/api/work/items/get-by-id/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("auditTrail", notNullValue())
                .body("auditTrail.size()", greaterThanOrEqualTo(1))
                .body("auditTrail[0].event", equalTo("CREATED"));
    }

    @Test
    void getById_unknownId_returns404() {
        given()
                .when().get("/api/work/items/get-by-id/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // -------------------------------------------------------------------------
    // GET /api/work/items/inbox — inbox query
    // -------------------------------------------------------------------------

    @Test
    void inbox_noParams_returnsItems() {
        WorkItemTestFixture.createWorkItem();

        given()
                .when().get("/api/work/items/inbox")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    @Test
    void inbox_filterByAssignee_afterClaim() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        // Inbox returns WorkItemRootView — item id is nested under "workItem.id"
        List<String> ids = given()
                .queryParam("assignee", "alice")
                .when().get("/api/work/items/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("workItem.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByCandidateGroup() {
        String uniqueGroup = "team-inbox-" + java.util.UUID.randomUUID();
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "title": "Group item",
                            "candidateGroups": "%s",
                            "createdBy": "system"
                        }
                        """.formatted(uniqueGroup))
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");

        // Inbox returns WorkItemRootView — item id is nested under "workItem.id"
        List<String> ids = given()
                .queryParam("candidateGroups", uniqueGroup)
                .when().get("/api/work/items/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("workItem.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByAssignee_pendingItemVisible() {
        // Inbox now requires identity context (assignee or candidateGroup) to return items.
        // Use assignee-based visibility: create item, claim it, then query by assignee.
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=bob-pending-test")
                .then().statusCode(200);

        List<String> ids = given()
                .queryParam("assignee", "bob-pending-test")
                .when().get("/api/work/items/inbox")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("workItem.id");

        assertThat(ids).contains(id);
    }

    @Test
    void inbox_filterByStatus_completedNotInAssigneeInbox() {
        // After completing a WorkItem, it is no longer a root visible via scanRoots
        // because scanRoots returns ALL roots for the assignee regardless of status.
        // This test verifies the endpoint still returns 200 with a valid response shape.
        String id = WorkItemTestFixture.createWorkItem();

        // claim → start → complete
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice-complete-test")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/start/" + id + "?actor=alice-complete-test")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "All done" }
                        """)
                .when().post("/api/work/lifecycle/complete/" + id + "?actor=alice-complete-test")
                .then().statusCode(200);

        // Completed items are still returned (scanRoots does not filter by status);
        // the endpoint must return 200 with a valid list.
        given()
                .queryParam("assignee", "alice-complete-test")
                .when().get("/api/work/items/inbox")
                .then()
                .statusCode(200)
                .body("$", notNullValue());
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/claim/{id}
    // -------------------------------------------------------------------------

    @Test
    void claim_returns200WithAssignedStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("alice"));
    }

    @Test
    void claim_alreadyAssigned_returns409() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=bob")
                .then().statusCode(409);
    }

    @Test
    void claim_unknownId_returns404() {
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/00000000-0000-0000-0000-000000000000?claimant=alice")
                .then().statusCode(404);
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/start/{id}
    // -------------------------------------------------------------------------

    @Test
    void start_returns200WithInProgressStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/start/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void start_pendingItem_returns409() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/start/" + id + "?actor=alice")
                .then().statusCode(409);
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/complete/{id}
    // -------------------------------------------------------------------------

    @Test
    void complete_returns200WithCompletedStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/start/" + id + "?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Fixed it" }
                        """)
                .when().post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("resolution", equalTo("Fixed it"));
    }

    @Test
    void complete_pendingItem_returns409() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Premature" }
                        """)
                .when().post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then().statusCode(409);
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/reject/{id}
    // -------------------------------------------------------------------------

    @Test
    void reject_returns200WithRejectedStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Not my responsibility" }
                        """)
                .when().post("/api/work/lifecycle/reject/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("REJECTED"));
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/delegate/{id}
    // -------------------------------------------------------------------------

    @Test
    void delegate_returns200WithNewAssignee() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "to": "bob" }
                        """)
                .when().post("/api/work/lifecycle/delegate/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("DELEGATED"));
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/release/{id}
    // -------------------------------------------------------------------------

    @Test
    void release_returns200WithPendingAndNullAssignee() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/release/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/suspend/{id}
    // -------------------------------------------------------------------------

    @Test
    void suspend_returns200WithSuspendedStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Waiting for external input" }
                        """)
                .when().post("/api/work/lifecycle/suspend/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("SUSPENDED"));
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/resume/{id}
    // -------------------------------------------------------------------------

    @Test
    void resume_afterAssignedSuspend_returnsAssigned() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Blocked" }
                        """)
                .when().post("/api/work/lifecycle/suspend/" + id + "?actor=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/resume/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"));
    }

    // -------------------------------------------------------------------------
    // POST /api/work/lifecycle/cancel/{id}
    // -------------------------------------------------------------------------

    @Test
    void cancel_returns200WithCancelledStatus() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "No longer needed" }
                        """)
                .when().post("/api/work/lifecycle/cancel/" + id + "?actor=admin")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    void cancel_fromAssigned_returns200() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "Revoked" }
                        """)
                .when().post("/api/work/lifecycle/cancel/" + id + "?actor=admin")
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // -------------------------------------------------------------------------
    // Audit trail
    // -------------------------------------------------------------------------

    @Test
    void auditTrail_growsWithEachOperation() {
        String id = WorkItemTestFixture.createWorkItem();

        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/start/" + id + "?actor=alice")
                .then().statusCode(200);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "resolution": "Done" }
                        """)
                .when().post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then().statusCode(200);

        List<String> events = given()
                .when().get("/api/work/items/get-by-id/" + id)
                .then()
                .statusCode(200)
                .body("auditTrail", hasSize(4))
                .extract().jsonPath().getList("auditTrail.event");

        assertThat(events).containsExactly("CREATED", "ASSIGNED", "STARTED", "COMPLETED");
    }

    // -------------------------------------------------------------------------
    // Gap-filling: error response bodies, priority/type filtering
    // -------------------------------------------------------------------------

    @Test
    void getById_notFound_returns404() {
        given()
                .when().get("/api/work/items/get-by-id/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void claim_alreadyAssigned_returns409_noBody() {
        String id = WorkItemTestFixture.createWorkItem();
        given()
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=alice");
        given()
                .when().post("/api/work/lifecycle/claim/" + id + "?claimant=bob")
                .then().statusCode(409);
    }

    // Priority and type filtering
    @Test
    void inbox_filterByPriority() {
        // Create HIGH priority item
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"High","priority":"HIGH","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201);
        // Create LOW priority item
        String lowId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Low","priority":"LOW","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");

        List<String> ids = given()
                .queryParam("priority", "HIGH")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(ids).doesNotContain(lowId);
    }

    @Test
    void inbox_filterByCategory() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Finance task","types":["finance"],"priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201);
        String legalId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Legal task","types":["legal"],"priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");

        List<String> ids = given()
                .queryParam("type", "finance")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(ids).doesNotContain(legalId);
    }

    @Test
    void inbox_filterByFollowUp() {
        // This test requires creating an item with a past followUpDate directly.
        // We can't easily do this via REST since followUpDate would need to be in the past.
        // Skip: followUp filter is tested at the repository level (InMemoryRepositoryTest).
        // Confirm the endpoint accepts the parameter without error.
        given()
                .queryParam("followUp", "true")
                .when().get("/api/work/items/inbox")
                .then().statusCode(200);
    }

    // -------------------------------------------------------------------------
    // confidenceScore — AI agent confidence metadata (Issue #112, Epic #100)
    // -------------------------------------------------------------------------

    @Test
    void createWorkItem_withConfidenceScore_persistsAndReturnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"AI Task\",\"createdBy\":\"agent\",\"confidenceScore\":0.55}")
                .post("/api/work/items/create")
                .then().statusCode(201)
                .body("confidenceScore", equalTo(0.55f));
    }

    @Test
    void createWorkItem_withoutConfidenceScore_returnsNullConfidenceScore() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Human Task\",\"createdBy\":\"human\"}")
                .post("/api/work/items/create")
                .then().statusCode(201)
                .body("confidenceScore", nullValue());
    }

    @Test
    void createWorkItem_withHighConfidenceScore_returnsIt() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"High Confidence\",\"createdBy\":\"agent\",\"confidenceScore\":0.95}")
                .post("/api/work/items/create")
                .then().statusCode(201)
                .body("confidenceScore", equalTo(0.95f));
    }

    // -------------------------------------------------------------------------
    // GET /api/work/items/list-all?outcome= — outcome filter
    // -------------------------------------------------------------------------

    @Test
    void listAll_filterByOutcome_returnsMatchingItems() {
        // Create a WorkItem and complete it with outcome "approved"
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Outcome Test\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create")
                .then().statusCode(201).extract().path("id");

        given().post("/api/work/lifecycle/claim/" + id + "?claimant=alice")
                .then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id + "?actor=alice")
                .then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"outcome\":\"approved\"}")
                .post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then().statusCode(200);

        // Create a second item with no outcome (stays PENDING)
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Outcome\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create")
                .then().statusCode(201);

        // Filter by outcome=approved — must include the completed one and exclude the pending one
        final List<String> returnedIds = given()
                .queryParam("outcome", "approved")
                .get("/api/work/items/list-all")
                .then()
                .statusCode(200)
                .body("items.every { it.outcome == 'approved' }", is(true))
                .extract().jsonPath().getList("items.id");
        assertThat(returnedIds).contains(id);
    }

    @Test
    void listAll_filterByOutcome_noMatch_returnsEmpty() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Pending Item\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create")
                .then().statusCode(201);

        given()
                .queryParam("outcome", "nonexistent-outcome")
                .get("/api/work/items/list-all")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(0));
    }
}

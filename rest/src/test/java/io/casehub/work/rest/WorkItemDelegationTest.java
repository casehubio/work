package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.casehub.work.rest.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;

/**
 * REST integration tests for delegation lifecycle endpoints.
 */
@QuarkusTest
@TestTransaction
class WorkItemDelegationTest {

    @Inject
    MutableCurrentPrincipal principal;

    private String createAndClaimItem(final String claimant) {
        final String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Delegation test","priority":"MEDIUM","createdBy":"system"}
                        """)
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");

        principal.setActorId(claimant);
        given().contentType(ContentType.JSON)
                .when().post("/api/work/lifecycle/claim/" + id)
                .then().statusCode(200);

        return id;
    }

    private void delegate(final String id, final String actor, final String to) {
        principal.setActorId(actor);
        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"" + to + "\"}")
                .when().post("/api/work/lifecycle/delegate/" + id)
                .then().statusCode(200);
    }

    private void delegateWithTarget(final String id, final String actor, final String to,
            final String declineTarget) {
        principal.setActorId(actor);
        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"" + to + "\",\"declineTarget\":\"" + declineTarget + "\"}")
                .when().post("/api/work/lifecycle/delegate/" + id)
                .then().statusCode(200);
    }

    // ── delegate endpoint ─────────────────────────────────────────────────────

    @Test
    void delegate_returns200WithDelegatedStatus() {
        final String id = createAndClaimItem("alice");

        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"bob\"}")
                .when().post("/api/work/lifecycle/delegate/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("status", equalTo("DELEGATED"))
                .body("assigneeId", equalTo("bob"));
    }

    @Test
    void delegate_withDeclineTargetDelegator_storesTarget() {
        final String id = createAndClaimItem("alice");

        given()
                .contentType(ContentType.JSON)
                .body("{\"to\":\"bob\",\"declineTarget\":\"DELEGATOR\"}")
                .when().post("/api/work/lifecycle/delegate/" + id + "?actor=alice")
                .then()
                .statusCode(200)
                .body("delegationDeclineTarget", equalTo("DELEGATOR"));
    }

    // ── GET /{id} — findById path ─────────────────────────────────────────────

    @Test
    void getById_afterDelegate_returnsDelegatedStatus() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        given()
                .when().get("/api/work/items/get-by-id/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("DELEGATED"));
    }

    // ── accept-delegation endpoint ────────────────────────────────────────────

    @Test
    void acceptDelegation_returns200WithAssignedStatus() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        principal.setActorId("bob");
        given()
                .when().post("/api/work/lifecycle/accept-delegation/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("bob"));
    }

    @Test
    void acceptDelegation_clearsDeclineTarget() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "DELEGATOR");

        principal.setActorId("bob");
        given()
                .when().post("/api/work/lifecycle/accept-delegation/" + id)
                .then()
                .statusCode(200)
                .body("delegationDeclineTarget", nullValue());
    }

    @Test
    void acceptDelegation_wrongActor_returns409() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        principal.setActorId("charlie");
        given()
                .when().post("/api/work/lifecycle/accept-delegation/" + id)
                .then().statusCode(409);
    }

    @Test
    void acceptDelegation_notDelegated_returns409() {
        final String id = createAndClaimItem("alice");

        // Item is ASSIGNED, not DELEGATED
        principal.setActorId("alice");
        given()
                .when().post("/api/work/lifecycle/accept-delegation/" + id)
                .then().statusCode(409);
    }

    // ── decline-delegation endpoint — POOL ───────────────────────────────────

    @Test
    void declineDelegation_poolPath_returns200WithPendingStatus() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "POOL");

        principal.setActorId("bob");
        given()
                .when().post("/api/work/lifecycle/decline-delegation/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── decline-delegation endpoint — DELEGATOR ───────────────────────────────

    @Test
    void declineDelegation_delegatorPath_returns200WithAssignedToPreviousActor() {
        final String id = createAndClaimItem("alice");
        delegateWithTarget(id, "alice", "bob", "DELEGATOR");

        principal.setActorId("bob");
        given()
                .when().post("/api/work/lifecycle/decline-delegation/" + id)
                .then()
                .statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo("alice"));
    }

    // ── decline-delegation error cases ────────────────────────────────────────

    @Test
    void declineDelegation_wrongActor_returns409() {
        final String id = createAndClaimItem("alice");
        delegate(id, "alice", "bob");

        principal.setActorId("charlie");
        given()
                .when().post("/api/work/lifecycle/decline-delegation/" + id)
                .then().statusCode(409);
    }

    @Test
    void declineDelegation_notDelegated_returns409() {
        final String id = createAndClaimItem("alice");

        principal.setActorId("alice");
        given()
                .when().post("/api/work/lifecycle/decline-delegation/" + id)
                .then().statusCode(409);
    }
}

package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.casehub.work.test.MutableCurrentPrincipal;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class WorkerSelectionStrategyIT {

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    @Test
    void leastLoaded_preAssigns_toCandidateUser_withLowestActiveCount() {
        final String alice = "ls-alice-" + System.nanoTime();
        final String bob = "ls-bob-" + System.nanoTime();
        // alice gets 2 active WorkItems
        createAndStartWorkItem(alice);
        createAndStartWorkItem(alice);
        // bob has 0

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Route Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("assigneeId", equalTo(bob))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void leastLoaded_noPreAssignment_whenNoCandidates() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"No Candidates\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("assigneeId", nullValue())
                .body("status", equalTo("PENDING"));
    }

    @Test
    void leastLoaded_preAssigns_toSingleCandidate() {
        final String actor = "solo-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Solo\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("assigneeId", equalTo(actor))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void preAssigned_workItem_isAlreadyAssigned_noClaimNeeded() {
        final String actor = "no-claim-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Pre-Assigned\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        // Should be ASSIGNED without any PUT /claim call
        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("status", equalTo("ASSIGNED"))
                .body("assigneeId", equalTo(actor));

        principal.setActorId(actor);
        given().post("/api/work/lifecycle/start/" + id)
                .then().statusCode(200)
                .body("status", equalTo("IN_PROGRESS"));
    }

    // ── candidateGroups with NoOpWorkerRegistry → PENDING ────────────────────

    @Test
    void candidateGroups_withNoOpRegistry_workItemStaysPending() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Group Only\",\"createdBy\":\"system\"," +
                        "\"candidateGroups\":\"some-group\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── requiredCapabilities → candidateUsers have no caps → PENDING ──────────

    @Test
    void requiredCapabilities_candidateUsersHaveNoCaps_workItemStaysPending() {
        final String actor = "cap-actor-" + System.nanoTime();
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Cap Required\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"," +
                        "\"requiredCapabilities\":\"exotic-skill\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    // ── RELEASED trigger ──────────────────────────────────────────────────────

    @Test
    void leastLoaded_refiresOnRelease_andReassigns() {
        final String alice = "rel-alice-" + System.nanoTime();
        final String bob = "rel-bob-" + System.nanoTime();

        // First WorkItem — alice has 0, bob has 0 → alice gets it (first in list)
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Release Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        // Whoever was assigned, give bob 3 extra active items
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);

        final String assignee = given().get("/api/work/items/get-by-id/" + id).then()
                .extract().path("assigneeId");
        principal.setActorId(assignee);
        given().post("/api/work/lifecycle/release/" + id).then().statusCode(200)
                .body("assigneeId", equalTo(alice))
                .body("status", equalTo("ASSIGNED"));
    }

    // ── DELEGATED trigger ─────────────────────────────────────────────────────

    @Test
    void leastLoaded_refiresOnDelegate_andReassigns() {
        final String alice = "del-alice-" + System.nanoTime();
        final String bob = "del-bob-" + System.nanoTime();
        final String carol = "del-carol-" + System.nanoTime();

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Delegate Me\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "," + carol + "\"}")
                .post("/api/work/items/create").then().statusCode(201)
                .extract().path("id");

        final String assignee = given().get("/api/work/items/get-by-id/" + id).then().statusCode(200)
                .extract().path("assigneeId");
        principal.setActorId(assignee);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);

        // Give bob 3 active items, carol 0
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);
        createAndStartWorkItem(bob);

        given().contentType(ContentType.JSON)
                .body("{\"to\":\"" + carol + "\"}")
                .post("/api/work/lifecycle/delegate/" + id)
                .then().statusCode(200)
                .body("assigneeId", equalTo(carol));
    }

    // ── E2E: full lifecycle with pre-assignment ───────────────────────────────

    @Test
    void e2e_preAssigned_fullLifecycle_noClaimRequired() {
        final String alice = "e2e-alice-" + System.nanoTime();
        final String bob = "e2e-bob-" + System.nanoTime();

        // alice has 2 active, bob has 0 → bob gets the new WorkItem
        createAndStartWorkItem(alice);
        createAndStartWorkItem(alice);

        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"E2E Work\",\"createdBy\":\"agent:ai\"," +
                        "\"candidateUsers\":\"" + alice + "," + bob + "\"," +
                        "\"confidenceScore\":0.9}")
                .post("/api/work/items/create").then().statusCode(201)
                .body("assigneeId", equalTo(bob))
                .body("status", equalTo("ASSIGNED"))
                .body("confidenceScore", equalTo(0.9f))
                .extract().path("id");

        principal.setActorId(bob);
        given().post("/api/work/lifecycle/start/" + id)
                .then().statusCode(200).body("status", equalTo("IN_PROGRESS"));

        given().contentType(ContentType.JSON).body("{\"resolution\":null}")
                .post("/api/work/lifecycle/complete/" + id)
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"))
                .body("assigneeId", equalTo(bob));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a WorkItem with only the given actor as candidateUser (so it gets pre-assigned
     * to that actor by LeastLoadedStrategy) then starts it to put it in IN_PROGRESS state.
     * This is the reliable way to add an active WorkItem to an actor's workload count.
     */
    private void createAndStartWorkItem(final String actor) {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Active\",\"createdBy\":\"system\"," +
                        "\"candidateUsers\":\"" + actor + "\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        principal.setActorId(actor);
        given().post("/api/work/lifecycle/start/" + id).then().statusCode(200);
    }
}

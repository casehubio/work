package io.casehub.work.queues.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueStateResourceTest {

    @Test
    void setRelinquishable_true_succeeds() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Soft assign test","createdBy":"alice","assigneeId":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .post("/api/work/queue-state/set-relinquishable/" + id).then()
                .statusCode(200).body("relinquishable", equalTo(true));
    }

    @Test
    void setRelinquishable_false_clearsFlag() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Clear flag test","createdBy":"alice","assigneeId":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .post("/api/work/queue-state/set-relinquishable/" + id).then().statusCode(200);

        given().contentType(ContentType.JSON).body("""
                {"relinquishable":false}""")
                .post("/api/work/queue-state/set-relinquishable/" + id).then()
                .statusCode(200).body("relinquishable", equalTo(false));
    }

    @Test
    void relinquishable_unknownWorkItem_returns404() {
        given().contentType(ContentType.JSON).body("""
                {"relinquishable":true}""")
                .post("/api/work/queue-state/set-relinquishable/00000000-0000-0000-0000-000000000000").then()
                .statusCode(404);
    }

    @Test
    void pickup_pendingItem_claimsIt() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Pickup pending test","createdBy":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().post("/api/work/queue-state/pickup/" + id + "?claimant=bob")
                .then().statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void pickup_relinquishableAssignedItem_succeedsAndClearsFlag() {
        // Create and claim a WorkItem
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Pickup relinquishable test","createdBy":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().post("/api/work/lifecycle/claim/" + id + "?claimant=alice").then().statusCode(200);

        // Mark as relinquishable
        given().contentType(ContentType.JSON)
                .body("""
                        {"relinquishable":true}""")
                .post("/api/work/queue-state/set-relinquishable/" + id).then().statusCode(200);

        // Bob picks it up from the queue
        given().post("/api/work/queue-state/pickup/" + id + "?claimant=bob")
                .then().statusCode(200)
                .body("assigneeId", equalTo("bob"))
                .body("status", equalTo("ASSIGNED"));

        // Flag is now cleared after bob's pickup — charlie cannot immediately take it
        // (bob has not set relinquishable, so the flag is false)
        given().post("/api/work/queue-state/pickup/" + id + "?claimant=charlie")
                .then().statusCode(409); // relinquishable was cleared on bob's pickup
    }

    @Test
    void pickup_nonRelinquishableAssignedItem_returns409() {
        var id = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Non-relinquishable test","createdBy":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().post("/api/work/lifecycle/claim/" + id + "?claimant=alice").then().statusCode(200);

        // No relinquishable flag set — pickup should be rejected
        given().post("/api/work/queue-state/pickup/" + id + "?claimant=bob")
                .then().statusCode(409);
    }

    @Test
    void pickup_unknownWorkItem_returns404() {
        given().post("/api/work/queue-state/pickup/00000000-0000-0000-0000-000000000000?claimant=bob")
                .then().statusCode(404);
    }
}

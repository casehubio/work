package io.casehub.work.queues.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class QueueResourceTest {

    @Test
    void createQueueView_returnsId() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Legal triage","labelPattern":"legal/**",
                      "scope":"TEAM","sortField":"createdAt","sortDirection":"ASC"}""")
               .post("/queues").then().statusCode(201)
               .body("id", notNullValue()).body("name", equalTo("Legal triage"));
    }

    @Test
    void listQueues_returnsCreated() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"List queue test","labelPattern":"intake/**","scope":"ORG"}""")
               .post("/queues").then().statusCode(201);
        given().get("/queues").then().statusCode(200).body("name", hasItem("List queue test"));
    }

    @Test
    @org.junit.jupiter.api.Disabled("Platform mismatch: SubjectViewOrchestrator.deleteView calls ViewMembershipTracker.getSubjectsByView which is not on the interface")
    void deleteQueueView_removesIt() {
        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"name":"Delete me queue","labelPattern":"x/**","scope":"ORG"}""")
                        .post("/queues").then().statusCode(201).extract().path("id");
        given().delete("/queues/" + id).then().statusCode(204);
    }

    @Test
    void getQueue_returnsWorkItemsMatchingLabel() {
        var queueId = given().contentType(ContentType.JSON)
                             .body("""
                                   {"name":"Queue member test","labelPattern":"priority/**",
                                    "scope":"ORG","sortField":"createdAt","sortDirection":"ASC"}""")
                             .post("/queues").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Queue test member","priority":"HIGH","createdBy":"alice"}""")
               .post("/workitems").then().statusCode(201);

        given().get("/queues/" + queueId).then().statusCode(200)
               .body("title", hasItem("Queue test member"));
    }

    @Test
    void getQueueView_unknownId_returns404() {
        given().get("/queues/00000000-0000-0000-0000-000000000000").then().statusCode(404);
    }

    @Test
    void getQueue_withAdditionalConditions_filtersToMatchingStatusOnly() {
        var queueId = given().contentType(ContentType.JSON)
                             .body("""
                                   {"name":"Pending only queue","labelPattern":"intake/**",
                                    "additionalConditions":"status.name() == 'PENDING'",
                                    "scope":"ORG"}""")
                             .post("/queues").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Pending item AC","createdBy":"alice"}""")
               .post("/workitems").then().statusCode(201);

        var assignedId = given().contentType(ContentType.JSON)
                                .body("""
                                      {"title":"Assigned item AC","createdBy":"alice"}""")
                                .post("/workitems").then().statusCode(201).extract().path("id");

        given().put("/workitems/" + assignedId + "/claim?claimant=bob")
               .then().statusCode(200);

        given().get("/queues/" + queueId).then()
               .statusCode(200)
               .body("title", hasItem("Pending item AC"))
               .body("title", not(hasItem("Assigned item AC")));
    }
}

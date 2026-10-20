package io.casehub.work.queues.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
               .post("/api/work/queues/create").then().statusCode(201)
               .body("id", notNullValue()).body("name", equalTo("Legal triage"));
    }

    @Test
    void listQueues_returnsCreated() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"List queue test","labelPattern":"intake/**","scope":"ORG"}""")
               .post("/api/work/queues/create").then().statusCode(201);
        given().get("/api/work/queues/list").then().statusCode(200).body("name", hasItem("List queue test"));
    }

    @Test
    void deleteQueueView_removesIt() {
        var id = given().contentType(ContentType.JSON)
                        .body("""
                              {"name":"Delete me queue","labelPattern":"x/**","scope":"ORG"}""")
                        .post("/api/work/queues/create").then().statusCode(201).extract().path("id");
        given().post("/api/work/queues/delete/" + id).then().statusCode(204);
    }

    @Test
    void getQueue_returnsWorkItemsMatchingLabel() {
        var queueId = given().contentType(ContentType.JSON)
                             .body("""
                                   {"name":"Queue member test","labelPattern":"priority/**",
                                    "scope":"ORG","sortField":"createdAt","sortDirection":"ASC"}""")
                             .post("/api/work/queues/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Queue test member","priority":"HIGH","createdBy":"alice"}""")
               .post("/api/work/items/create").then().statusCode(201);

        given().get("/api/work/queues/query/" + queueId).then().statusCode(200)
               .body("title", hasItem("Queue test member"));
    }

    @Test
    void getQueueView_unknownId_returns404() {
        given().get("/api/work/queues/query/00000000-0000-0000-0000-000000000000").then().statusCode(404);
    }

    @Test
    void listQueues_includesSummaryPerQueue() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Enriched list rule","scope":"ORG","conditionLanguage":"jexl",
                      "conditionExpression":"priority == 'HIGH'",
                      "actions":[{"type":"Add","label":"enriched-test/items"}]}""")
               .post("/api/work/label-rules/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Enriched list queue","labelPattern":"enriched-test/**","scope":"ORG"}""")
               .post("/api/work/queues/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Enriched list item","priority":"HIGH","createdBy":"alice"}""")
               .post("/api/work/items/create").then().statusCode(201);

        given().get("/api/work/queues/list").then()
               .statusCode(200)
               .body("find { it.name == 'Enriched list queue' }.summary.total",
                     greaterThanOrEqualTo(1))
               .body("find { it.name == 'Enriched list queue' }.summary.byStatus.PENDING",
                     greaterThanOrEqualTo(1));
    }

    @Test
    void getQueue_withAdditionalConditions_filtersToMatchingStatusOnly() {
        var queueId = given().contentType(ContentType.JSON)
                             .body("""
                                   {"name":"Pending only queue","labelPattern":"intake/**",
                                    "additionalConditions":"status.name() == 'PENDING'",
                                    "scope":"ORG"}""")
                             .post("/api/work/queues/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Pending item AC","createdBy":"alice"}""")
               .post("/api/work/items/create").then().statusCode(201);

        var assignedId = given().contentType(ContentType.JSON)
                                .body("""
                                      {"title":"Assigned item AC","createdBy":"alice"}""")
                                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().post("/api/work/lifecycle/claim/" + assignedId + "?claimant=bob")
               .then().statusCode(200);

        given().get("/api/work/queues/query/" + queueId).then()
               .statusCode(200)
               .body("title", hasItem("Pending item AC"))
               .body("title", not(hasItem("Assigned item AC")));
    }
}

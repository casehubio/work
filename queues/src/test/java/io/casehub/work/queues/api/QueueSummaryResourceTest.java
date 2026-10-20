package io.casehub.work.queues.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueSummaryResourceTest {

    @Test
    void summary_returnsAggregatesForQueueMembers() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Summary filter","scope":"ORG","conditionLanguage":"jexl",
                         "conditionExpression":"priority == 'HIGH' || priority == 'MEDIUM'",
                         "actions":[{"type":"Add","label":"summary-test/items"}]}""")
                .post("/api/work/label-rules/create").then().statusCode(201);

        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Summary test queue","labelPattern":"summary-test/**","scope":"ORG"}""")
                .post("/api/work/queues/create").then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Summary item 1","priority":"HIGH","createdBy":"alice"}""")
                .post("/api/work/items/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Summary item 2","priority":"MEDIUM","createdBy":"bob"}""")
                .post("/api/work/items/create").then().statusCode(201);

        given().get("/api/work/queues/summary/" + queueId).then()
                .statusCode(200)
                .body("total", greaterThanOrEqualTo(2))
                .body("byStatus.PENDING", greaterThanOrEqualTo(2))
                .body("byPriority.HIGH", greaterThanOrEqualTo(1))
                .body("byPriority.MEDIUM", greaterThanOrEqualTo(1))
                .body("oldestCreatedAt", notNullValue());
    }

    @Test
    void summary_emptyQueue_returnsZeroCounts() {
        var queueId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Empty summary queue","labelPattern":"no-match-ever-xyz/**","scope":"ORG"}""")
                .post("/api/work/queues/create").then().statusCode(201).extract().path("id");

        given().get("/api/work/queues/summary/" + queueId).then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("byStatus", anEmptyMap())
                .body("byPriority", anEmptyMap())
                .body("overdue", equalTo(0))
                .body("claimDeadlineBreached", equalTo(0))
                .body("oldestCreatedAt", nullValue());
    }

    @Test
    void summary_unknownQueue_returns404() {
        given().get("/api/work/queues/summary/00000000-0000-0000-0000-000000000000").then()
                .statusCode(404);
    }
}

package io.casehub.work.queues.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class QueueHealthResourceTest {

    @Test
    void health_returnsKpiMetrics() {
        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Health rule","scope":"ORG","conditionLanguage":"jexl",
                      "conditionExpression":"priority == 'HIGH' || priority == 'MEDIUM'",
                      "actions":[{"type":"Add","label":"health-test/items"}]}""")
               .post("/api/work/label-rules/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
               .body("""
                     {"name":"Health test queue","labelPattern":"health-test/**","scope":"ORG"}""")
               .post("/api/work/queues/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Health item 1","priority":"HIGH","createdBy":"alice"}""")
               .post("/api/work/items/create").then().statusCode(201);

        given().contentType(ContentType.JSON)
               .body("""
                     {"title":"Health item 2","priority":"MEDIUM","createdBy":"bob"}""")
               .post("/api/work/items/create").then().statusCode(201);

        given().get("/api/work/queues/health").then()
               .statusCode(200)
               .body("key", hasItems("total", "pending", "active", "overdue", "breached"))
               .body("find { it.key == 'total' }.value", greaterThanOrEqualTo(2))
               .body("find { it.key == 'pending' }.value", greaterThanOrEqualTo(2))
               .body("find { it.key == 'total' }.label", equalTo("Total"))
               .body("find { it.key == 'pending' }.status", equalTo("warning"));
    }

    @Test
    void health_returnsAllFiveMetrics() {
        given().get("/api/work/queues/health").then()
               .statusCode(200)
               .body("key", hasItems("total", "pending", "active", "overdue", "breached"))
               .body("size()", equalTo(5));
    }
}

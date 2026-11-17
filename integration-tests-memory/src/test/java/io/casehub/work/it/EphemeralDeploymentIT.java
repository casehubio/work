package io.casehub.work.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;

/**
 * Ephemeral deployment integration test — verifies CaseHub Work boots and
 * serves WorkItem CRUD through in-memory stores from
 * {@code casehub-work-persistence-memory} (dummy H2 datasource, no Flyway).
 */
@QuarkusIntegrationTest
class EphemeralDeploymentIT {

    private String createWorkItem(String title) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "%s",
                          "priority": "MEDIUM",
                          "createdBy": "ephemeral-test"
                        }
                        """.formatted(title))
                .when().post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void create_returns201WithPendingStatus() {
        String id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Ephemeral create test",
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
                .extract().path("id");

        assertThat(id).isNotNull();
    }

    @Test
    void getById_returnsWorkItemWithAuditTrail() {
        String id = createWorkItem("Get by id ephemeral");
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
    void getById_unknownId_returns404() {
        given()
                .when().get("/api/work/items/get-by-id/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("error", notNullValue());
    }

    @Test
    void listAll_returnsNonEmptyArray() {
        createWorkItem("List test ephemeral");
        given()
                .when().get("/api/work/items/list-all")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(1));
    }

    @Test
    void fullLifecycle_createClaimStartComplete() {
        String id = createWorkItem("Full lifecycle ephemeral");

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
    void rejectPath() {
        String id = createWorkItem("Reject ephemeral");
        given().post("/api/work/lifecycle/claim/" + id);
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"out of scope\"}")
                .post("/api/work/lifecycle/reject/" + id)
                .then().statusCode(200).body("status", equalTo("REJECTED"));
    }

    @Test
    void releasePath_returnsToPending() {
        String id = createWorkItem("Release ephemeral");
        given().post("/api/work/lifecycle/claim/" + id);
        given().post("/api/work/lifecycle/release/" + id)
                .then().statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("assigneeId", nullValue());
    }

    @Test
    void cancelPath() {
        String id = createWorkItem("Cancel ephemeral");
        given().contentType(ContentType.JSON)
                .body("{\"reason\":\"no longer needed\"}")
                .post("/api/work/lifecycle/cancel/" + id)
                .then().statusCode(200).body("status", equalTo("CANCELLED"));
    }

    @Test
    void inbox_returns200() {
        createWorkItem("Inbox ephemeral");
        given()
                .when().get("/api/work/items/inbox")
                .then().statusCode(200);
    }

    @Test
    void auditTrail_fourEntriesAfterFullPath() {
        String id = createWorkItem("Audit ephemeral");
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
}

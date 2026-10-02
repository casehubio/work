package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that WorkItemService validates payload against inputDataSchema at create()
 * and resolution against outputDataSchema at complete(). Refs #170.
 */
@QuarkusTest
class WorkItemSchemaValidationTest {

    @Inject
    EntityManager em;

    private static final String OUTPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"decision\"]," +
            "\"properties\":{\"decision\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"requestor\"]," +
            "\"properties\":{\"requestor\":{\"type\":\"string\"}},\"additionalProperties\":false}";

    @BeforeEach
    @Transactional
    void clearTemplates() {
        em.createQuery("DELETE FROM WorkItemTemplate").executeUpdate();
    }

    // ── outputDataSchema (resolution validation) ─────────────────────────────

    @Test
    void complete_validResolution_returns200() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"decision\\\":\\\"approved\\\"}\",\"outcome\":null}")
                .post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then()
                .statusCode(200);
    }

    @Test
    void complete_invalidResolution_returns400WithViolations() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"wrong_field\\\":\\\"value\\\"}\"}")
                .post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then()
                .statusCode(400)
                .body("error", containsString("outputDataSchema"));
    }

    @Test
    void complete_nullResolution_whenOutputSchemaSet_returns200() {
        final String id = workItemReadyToComplete(OUTPUT_SCHEMA);

        given().contentType(ContentType.JSON)
                .body("{}")
                .post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then()
                .statusCode(200);
    }

    @Test
    void complete_noOutputSchema_anyResolutionAccepted() {
        final String id = workItemReadyToComplete(null);

        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{\\\"anything\\\":true}\"}")
                .post("/api/work/lifecycle/complete/" + id + "?actor=alice")
                .then()
                .statusCode(200);
    }

    // ── inputDataSchema (payload validation at create) ────────────────────────

    @Test
    void instantiate_validDefaultPayload_returns201() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Input Schema Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + "," +
                      "\"defaultPayload\":\"{\\\"requestor\\\":\\\"eng-team\\\"}\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/api/work/templates/create")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/api/work/templates/instantiate/" + templateId)
                .then()
                .statusCode(201);
    }

    @Test
    void instantiate_invalidDefaultPayload_returns400() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bad Payload Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + "," +
                      "\"defaultPayload\":\"{\\\"wrong_field\\\":\\\"value\\\"}\"," +
                      "\"createdBy\":\"admin\"}")
                .post("/api/work/templates/create")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/api/work/templates/instantiate/" + templateId)
                .then()
                .statusCode(400);
    }

    @Test
    void instantiate_nullPayload_whenInputSchemaSet_returns201() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Null Payload Template\",\"candidateGroups\":\"ops\"," +
                      "\"inputDataSchema\":" + INPUT_SCHEMA + ",\"createdBy\":\"admin\"}")
                .post("/api/work/templates/create")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/api/work/templates/instantiate/" + templateId)
                .then()
                .statusCode(201);
    }

    @Test
    void directCreate_noTemplate_noSchemaValidation() {
        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Ad hoc\",\"candidateGroups\":\"ops\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create")
                .then()
                .statusCode(201);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String workItemReadyToComplete(final String outputDataSchema) {
        final String schemaJson = outputDataSchema != null
                ? ",\"outputDataSchema\":" + outputDataSchema
                : "";
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Completion Schema\",\"candidateGroups\":\"reviewers\"" +
                      schemaJson + ",\"createdBy\":\"admin\"}")
                .post("/api/work/templates/create")
                .then().statusCode(201).extract().path("id");

        final String id = given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/api/work/templates/instantiate/" + templateId)
                .then().statusCode(201).extract().path("id");

        given().post("/api/work/lifecycle/claim/" + id + "?claimant=alice").then().statusCode(200);
        given().post("/api/work/lifecycle/start/" + id + "?actor=alice").then().statusCode(200);
        return id;
    }
}

package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Tests for PATCH /workitem-templates/{id} — JSON Merge Patch (RFC 7396). Refs #199.
 */
@QuarkusTest
class WorkItemTemplatePatchTest {

    private static final String MERGE_PATCH = "application/merge-patch+json";

    @BeforeEach
    @Transactional
    void clearAll() {
        AuditEntry.deleteAll();
        WorkItem.deleteAll();
        WorkItemTemplate.deleteAll();
    }

    private String createTemplate() {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Template Alpha","description":"Original description",
                         "category":"legal","priority":"HIGH",
                         "candidateGroups":"lawyers","candidateUsers":"bob",
                         "requiredCapabilities":"legal-review",
                         "defaultExpiryHours":48,"defaultClaimHours":8,
                         "allowSameAssignee":true,
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");
    }

    // ── Generic absent / present / null semantics ───────────────────────────

    @Test
    void patch_absentField_leavesExistingValueUnchanged() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"category\":\"compliance\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("category", equalTo("compliance"))
                .body("description", equalTo("Original description"))  // unchanged
                .body("candidateGroups", equalTo("lawyers"));           // unchanged
    }

    @Test
    void patch_presentField_updatesValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"description\":\"Updated description\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("description", equalTo("Updated description"));
    }

    @Test
    void patch_nullField_clearsValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"description\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("description", nullValue());
    }

    @Test
    void patch_emptyBody_noChanges() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Template Alpha"))
                .body("description", equalTo("Original description"));
    }

    @Test
    void patch_nonExistentTemplate_returns404() {
        given().contentType(MERGE_PATCH)
                .body("{\"description\":\"nope\"}")
                .patch("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // ── Integer fields ───────────────────────────────────────────────────────

    @Test
    void patch_integerField_updatesValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"defaultExpiryHours\":72}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("defaultExpiryHours", equalTo(72));
    }

    @Test
    void patch_integerField_null_clearsValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"defaultClaimHours\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("defaultClaimHours", nullValue());
    }

    // ── Boolean fields ───────────────────────────────────────────────────────

    @Test
    void patch_booleanField_updatesValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"allowSameAssignee\":false}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("allowSameAssignee", equalTo(false));
    }

    @Test
    void patch_booleanField_null_clearsValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"allowSameAssignee\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("allowSameAssignee", nullValue());
    }

    // ── name special handling ────────────────────────────────────────────────

    @Test
    void patch_name_null_returns400() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"name\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(400);
    }

    @Test
    void patch_name_conflictWithOtherTemplate_returns409() {
        createTemplate(); // "Template Alpha"
        final String id2 = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Template Beta\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(MERGE_PATCH)
                .body("{\"name\":\"Template Alpha\"}")
                .patch("/workitem-templates/" + id2)
                .then()
                .statusCode(409);
    }

    @Test
    void patch_name_sameValueAsCurrentName_returns200() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"name\":\"Template Alpha\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Template Alpha"));
    }

    // ── priority special handling ────────────────────────────────────────────

    @Test
    void patch_priority_validValue_updatesEnum() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"priority\":\"URGENT\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("priority", equalTo("URGENT"));
    }

    @Test
    void patch_priority_invalidValue_returns400() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"priority\":\"NOT_A_PRIORITY\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(400);
    }

    @Test
    void patch_priority_null_clearsValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"priority\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("priority", nullValue());
    }

    // ── outcomes special handling ────────────────────────────────────────────

    @Test
    void patch_outcomes_setsAndRoundTrips() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("""
                        {"outcomes":[
                          {"name":"approved","displayName":"Approved","condition":null},
                          {"name":"rejected","displayName":"Rejected","condition":"reason != null"}
                        ]}
                        """)
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("outcomes", hasSize(2))
                .body("outcomes[0].name", equalTo("approved"))
                .body("outcomes[1].name", equalTo("rejected"))
                .body("outcomes[1].condition", equalTo("reason != null"));
    }

    @Test
    void patch_outcomes_null_clearsValue() {
        // First set outcomes
        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Template With Outcomes","createdBy":"admin",
                         "outcomes":[{"name":"approved","displayName":"Approved","condition":null}]}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        // Then clear via PATCH
        given().contentType(MERGE_PATCH)
                .body("{\"outcomes\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("outcomes", nullValue());
    }

    // ── inputDataSchema / outputDataSchema special handling ──────────────────

    @Test
    void patch_inputDataSchema_setsJsonObject() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"inputDataSchema\":{\"type\":\"object\",\"required\":[\"name\"]}}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("inputDataSchema", notNullValue());
    }

    @Test
    void patch_inputDataSchema_null_clearsValue() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"inputDataSchema\":null}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("inputDataSchema", nullValue());
    }

    @Test
    void patch_inputDataSchema_nonObject_returns400() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"inputDataSchema\":[\"not\",\"an\",\"object\"]}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(400);
    }

    @Test
    void patch_outputDataSchema_nonObject_returns400() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"outputDataSchema\":\"just a string\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(400);
    }

    // ── createdBy silently ignored ────────────────────────────────────────────

    @Test
    void patch_createdBy_isIgnored_originalAuthorPreserved() {
        final String id = createTemplate();

        given().contentType(MERGE_PATCH)
                .body("{\"createdBy\":\"hacker\",\"description\":\"changed\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("createdBy", equalTo("admin"))       // unchanged
                .body("description", equalTo("changed")); // applied
    }
}

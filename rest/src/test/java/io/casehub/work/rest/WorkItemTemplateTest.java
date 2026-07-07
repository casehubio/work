package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and end-to-end tests for WorkItemTemplate.
 *
 * <h2>What templates are for</h2>
 * <p>
 * A template pre-defines the category, priority, candidate groups, expiry defaults,
 * payload schema, and labels for a repeatable process. Instantiating one creates a
 * fully-configured WorkItem in a single call — no need to repeat the same 15-field
 * body every time a loan application arrives, a security incident is reported, etc.
 *
 * <h2>Test tiers</h2>
 * <ul>
 * <li><strong>Unit</strong> — template-to-WorkItem mapping (WorkItemTemplateServiceTest)</li>
 * <li><strong>Integration</strong> — CRUD for templates via REST</li>
 * <li><strong>Happy path</strong> — create template, instantiate it, verify WorkItem fields</li>
 * <li><strong>E2E</strong> — override defaults at instantiation time</li>
 * </ul>
 */
@QuarkusTest
class WorkItemTemplateTest {

    @BeforeEach
    @Transactional
    void clearTemplates() {
        WorkItemTemplate.deleteAll();
    }

    // ── POST /workitem-templates ──────────────────────────────────────────────

    @Test
    void createTemplate_returns201_withId() {
        given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Loan Approval","typePaths":"[\\"finance\\"]","priority":"HIGH",
                         "candidateGroups":"loan-officers","defaultExpiryHours":48,
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Loan Approval"))
                .body("typePaths", equalTo("[\"finance\"]"))
                .body("priority", equalTo("HIGH"))
                .body("candidateGroups", equalTo("loan-officers"))
                .body("defaultExpiryHours", equalTo(48))
                .body("createdBy", equalTo("admin"));
    }

    @Test
    void createTemplate_returns400_whenNameMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"typePaths\":\"[\\\"finance\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void createTemplate_returns409_whenNameAlreadyExists() {
        final String body = "{\"name\":\"Duplicate Name\",\"createdBy\":\"admin\"}";
        given().contentType(ContentType.JSON).body(body).post("/workitem-templates").then().statusCode(201);
        given().contentType(ContentType.JSON).body(body).post("/workitem-templates").then().statusCode(409);
    }

    @Test
    void createTemplate_returns400_whenCreatedByMissing() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Test\",\"typePaths\":\"[\\\"ops\\\"]\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(400);
    }

    @Test
    void createTemplate_withMinimalFields_succeeds() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Minimal Template\",\"createdBy\":\"alice\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("typePaths", nullValue())
                .body("priority", nullValue())
                .body("defaultExpiryHours", nullValue());
    }

    // ── GET /workitem-templates ───────────────────────────────────────────────

    @Test
    void listTemplates_includesCreatedTemplate() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Security Triage Template\",\"typePaths\":\"[\\\"security\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201);

        given().get("/workitem-templates")
                .then()
                .statusCode(200)
                .body("name", hasItem("Security Triage Template"));
    }

    // ── GET /workitem-templates/{id} ──────────────────────────────────────────

    @Test
    void getTemplate_returnsById() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Compliance Review\",\"typePaths\":\"[\\\"legal\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().get("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("Compliance Review"))
                .body("typePaths", equalTo("[\"legal\"]"));
    }

    @Test
    void getTemplate_returns404_forUnknownId() {
        given().get("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    // ── DELETE /workitem-templates/{id} ───────────────────────────────────────

    @Test
    void deleteTemplate_returns204_andTemplateIsGone() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"To Delete\",\"createdBy\":\"alice\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().delete("/workitem-templates/" + id).then().statusCode(204);
        given().get("/workitem-templates/" + id).then().statusCode(404);
    }

    @Test
    void deleteTemplate_returns404_forUnknownId() {
        given().delete("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── Happy path: instantiate template ─────────────────────────────────────

    @Test
    void instantiate_createsWorkItemWithTemplateDefaults() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"NDA Review","typePaths":"[\\"legal\\"]","priority":"HIGH",
                         "candidateGroups":"legal-team","defaultExpiryHours":72,
                         "defaultPayload":"{\\\"type\\\":\\\"nda\\\"}",
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("types[0]", equalTo("legal"))
                .body("priority", equalTo("HIGH"))
                .body("candidateGroups", equalTo("legal-team"))
                .body("payload", equalTo("{\"type\":\"nda\"}"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void instantiate_titleDefaultsToTemplateName() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Standard Security Review\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"agent-1\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("title", equalTo("Standard Security Review"));
    }

    // ── E2E: override defaults at instantiation time ──────────────────────────

    @Test
    void instantiate_withTitleOverride_usesProvidedTitle() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Finance Review\",\"typePaths\":\"[\\\"finance\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"title\":\"Q4 budget reallocation — £50k\",\"createdBy\":\"finance-bot\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("title", equalTo("Q4 budget reallocation — £50k"))
                .body("types[0]", equalTo("finance")); // still from template
    }

    @Test
    void instantiate_withAssigneeOverride_assignsDirectly() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Direct Assignment\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"assigneeId\":\"alice\",\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("assigneeId", equalTo("alice"));
    }

    @Test
    void instantiate_returns404_forUnknownTemplate() {
        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/00000000-0000-0000-0000-000000000000/instantiate")
                .then()
                .statusCode(404);
    }

    @Test
    void instantiate_preservesTemplateLabels() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Labelled Template","typePaths":"[\\"ops\\"]",
                         "labelPaths":"[\\"intake/triage\\",\\"priority/high\\"]",
                         "createdBy":"admin"}
                        """)
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"system\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("labels.path", hasItem("intake/triage"))
                .body("labels.path", hasItem("priority/high"))
                .body("labels.findAll { it.persistence == 'MANUAL' }", hasSize(2));
    }

    // ── PUT /workitem-templates/{id} ─────────────────────────────────────────

    @Test
    void updateTemplate_returns200_withUpdatedFields() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Original\",\"typePaths\":\"[\\\"legal\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Updated\",\"typePaths\":\"[\\\"finance\\\"]\",\"candidateGroups\":\"ops\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated"))
                .body("typePaths", equalTo("[\"finance\"]"))
                .body("candidateGroups", equalTo("ops"))
                .body("createdBy", equalTo("admin"));
    }

    @Test
    void updateTemplate_clearsFieldsWhenNull() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"WithDesc\",\"description\":\"old desc\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"WithDesc\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("description", nullValue());
    }

    @Test
    void updateTemplate_returns404_whenNotFound() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Whatever\"}")
                .put("/workitem-templates/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404);
    }

    @Test
    void updateTemplate_returns400_whenNameBlank() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"ToUpdate\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(400);
    }

    @Test
    void updateTemplate_returns409_whenNameConflictsWithOtherTemplate() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"AlreadyExists\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201);

        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"ToRename\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"AlreadyExists\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(409);
    }

    @Test
    void updateTemplate_allowsSameNameOnSameTemplate() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"SameName\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"SameName\",\"typePaths\":\"[\\\"finance\\\"]\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("name", equalTo("SameName"))
                .body("typePaths", equalTo("[\"finance\"]"));
    }

    // ── Template versioning (#180) ───────────────────────────────────────────

    @Test
    void createTemplate_startsAtVersion1() {
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Versioned\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then()
                .statusCode(201)
                .body("version", equalTo(1));
    }

    @Test
    void updateTemplate_incrementsVersion() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bump\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bump\",\"typePaths\":\"[\\\"updated\\\"]\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("version", equalTo(2));

        given().contentType(ContentType.JSON)
                .body("{\"name\":\"Bump\",\"typePaths\":\"[\\\"updated-again\\\"]\"}")
                .put("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("version", equalTo(3));
    }

    @Test
    void patchTemplate_incrementsVersion() {
        final String id = given().contentType(ContentType.JSON)
                .body("{\"name\":\"PatchBump\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        given().contentType("application/merge-patch+json")
                .body("{\"typePaths\":\"[\\\"patched\\\"]\"}")
                .patch("/workitem-templates/" + id)
                .then()
                .statusCode(200)
                .body("version", equalTo(2));
    }

    @Test
    void instantiate_setsTemplateVersionOnWorkItem() {
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"InstVer\",\"typePaths\":\"[\\\"ops\\\"]\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201).extract().path("id");

        // Update to version 2
        given().contentType(ContentType.JSON)
                .body("{\"name\":\"InstVer\",\"typePaths\":\"[\\\"ops-v2\\\"]\"}")
                .put("/workitem-templates/" + templateId)
                .then().statusCode(200);

        // Instantiate — should record version 2
        given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"alice\"}")
                .post("/workitem-templates/" + templateId + "/instantiate")
                .then()
                .statusCode(201)
                .body("templateVersion", equalTo(2));
    }
}

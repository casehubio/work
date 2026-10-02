package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class WorkItemCloneTest {

    @Test
    void clone_returns201_withNewId() {
        final String sourceId = createFull();

        final String cloneId = given().contentType(ContentType.JSON)
                .body("{\"createdBy\":\"alice\"}")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("PENDING"))
                .extract().path("id");

        // Different ID from source
        org.assertj.core.api.Assertions.assertThat(cloneId).isNotEqualTo(sourceId);
    }

    @Test
    void clone_copiesOperationalFields() {
        final String sourceId = createFull();

        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("title", containsString("Full item"))
                .body("types[0]", equalTo("test-category"))
                .body("priority", equalTo("HIGH"))
                .body("candidateGroups", equalTo("team-a"))
                .body("payload", equalTo("{\"key\":\"value\"}"))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void clone_defaultTitle_appendsCopySuffix() {
        final String sourceId = createFull();

        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("title", containsString("(copy)"));
    }

    @Test
    void clone_withTitleOverride_usesProvidedTitle() {
        final String sourceId = createFull();

        given().queryParam("title", "Custom clone title")
                .queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("title", equalTo("Custom clone title"));
    }

    @Test
    void clone_doesNotCopyAssignee_orOwner() {
        final String sourceId = given().contentType(ContentType.JSON)
                .body("{\"title\":\"Assigned item\",\"createdBy\":\"sys\",\"assigneeId\":\"bob\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("assigneeId", nullValue())
                .body("owner", nullValue());
    }

    @Test
    void clone_doesNotCopyResolution_orDelegationChain() {
        // Complete the source through claim → start → complete lifecycle
        final String sourceId = createFull();
        given().queryParam("claimant", "bob").post("/api/work/lifecycle/claim/" + sourceId + "").then().statusCode(200);
        given().queryParam("actor", "bob").post("/api/work/lifecycle/start/" + sourceId + "").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"resolution\":\"{}\"}")
                .queryParam("actor", "bob")
                .post("/api/work/lifecycle/complete/" + sourceId + "").then().statusCode(200);

        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("status", equalTo("PENDING"))
                .body("resolution", nullValue())
                .body("delegationChain", nullValue());
    }

    @Test
    void clone_copiesManualLabels_notInferred() {
        // Item with a MANUAL label
        final String sourceId = given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Labelled","createdBy":"sys",
                         "labels":[{"path":"legal/review","persistence":"MANUAL","appliedBy":"alice"}]}
                        """)
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");

        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/" + sourceId)
                .then().statusCode(201)
                .body("labels.path", hasItem("legal/review"))
                .body("labels.findAll{it.persistence=='MANUAL'}.size()", equalTo(1));
    }

    @Test
    void clone_returns404_forUnknownSource() {
        given().queryParam("createdBy", "alice")
                .post("/api/work/items/clone/00000000-0000-0000-0000-000000000000")
                .then().statusCode(404);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String createFull() {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"title":"Full item","types":["test-category"],"priority":"HIGH",
                         "candidateGroups":"team-a","createdBy":"sys",
                         "payload":"{\\"key\\":\\"value\\"}"}
                        """)
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
    }
}

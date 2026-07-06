package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
class LabelEndpointTest {

    @Test
    void createWorkItem_withManualLabel_returnedInResponse() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Review contract",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(1))
                .body("labels[0].path", equalTo("legal/contracts"))
                .body("labels[0].persistence", equalTo("MANUAL"))
                .body("labels[0].appliedBy", equalTo("alice"));
    }

    @Test
    void createWorkItem_withInferredLabel_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Review contract",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "INFERRED", "appliedBy": null}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(400);
    }

    @Test
    void createWorkItem_withNoLabels_returnsEmptyList() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "No labels",
                          "createdBy": "bob"
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(0));
    }

    @Test
    void createWorkItem_withMultipleLabels_allReturned() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Multi-label item",
                          "createdBy": "alice",
                          "labels": [
                            {"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"},
                            {"path": "priority/high",   "persistence": "MANUAL", "appliedBy": "alice"}
                          ]
                        }
                        """)
                .post("/workitems")
                .then()
                .statusCode(201)
                .body("labels", hasSize(2));
    }

    @Test
    void vocabulary_listAll_includesSeededGlobalTerms() {
        given()
                .get("/vocabulary")
                .then()
                .statusCode(200)
                .body("path", org.hamcrest.Matchers.hasItem("legal/contracts"))
                .body("path", org.hamcrest.Matchers.hasItem("intake"));
    }

    @Test
    void vocabulary_addDefinition_appearsInList() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "test/unique-vocab-54", "description": "test label", "addedBy": "alice"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201)
                .body("path", equalTo("test/unique-vocab-54"));

        given()
                .get("/vocabulary")
                .then()
                .statusCode(200)
                .body("path", org.hamcrest.Matchers.hasItem("test/unique-vocab-54"));
    }

    @Test
    void vocabulary_addDefinition_invalidScope_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "test/label", "addedBy": "alice", "scope": "acme//team"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("invalid scope"));
    }

    @Test
    void vocabulary_addDefinition_orgScope_succeeds() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "org/finance/approvals", "addedBy": "alice", "scope": "acme-corp"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201)
                .body("path", equalTo("org/finance/approvals"))
                .body("scope", equalTo("acme-corp"));
    }

    @Test
    void vocabulary_addDefinition_teamScope_succeeds() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "team/sprint/review", "addedBy": "bob", "scope": "acme-corp/team-alpha"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201)
                .body("path", equalTo("team/sprint/review"))
                .body("scope", equalTo("acme-corp/team-alpha"));
    }

    @Test
    void vocabulary_addDefinition_scopedTerm_appearsInListAll() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "my/team/label", "addedBy": "charlie", "scope": "acme-corp/team-bravo"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201);

        given()
                .get("/vocabulary")
                .then()
                .statusCode(200)
                .body("path", org.hamcrest.Matchers.hasItem("my/team/label"));
    }

    @Test
    void vocabulary_addDefinition_sameScope_reuseVocabulary() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "personal/label/one", "addedBy": "dave", "scope": "casehubio/dave"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "personal/label/two", "addedBy": "dave", "scope": "casehubio/dave"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(201);

        given()
                .get("/vocabulary")
                .then()
                .statusCode(200)
                .body("path", org.hamcrest.Matchers.hasItem("personal/label/one"))
                .body("path", org.hamcrest.Matchers.hasItem("personal/label/two"));
    }

    @Test
    void vocabulary_addDefinition_wildcardPath_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "legal/*", "addedBy": "alice"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("wildcard"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"path": "legal/?", "addedBy": "alice"}
                        """)
                .post("/vocabulary")
                .then()
                .statusCode(400)
                .body("error", org.hamcrest.Matchers.containsString("wildcard"));
    }

    @Test
    void vocabulary_addDefinition_pathStoredAndRetrievableByPath() {
        String uniquePath = "test/converter-round-trip-99";

        given()
                .contentType(ContentType.JSON)
                .body("{\"path\": \"" + uniquePath + "\", \"description\": \"converter test\", \"addedBy\": \"alice\"}")
                .post("/vocabulary")
                .then()
                .statusCode(201)
                .body("path", equalTo(uniquePath));

        given()
                .get("/vocabulary")
                .then()
                .statusCode(200)
                .body("path", org.hamcrest.Matchers.hasItem(uniquePath));
    }

    @Test
    void vocabulary_addDefinition_emptyPath_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"path\": \"\", \"addedBy\": \"alice\"}")
                .post("/vocabulary")
                .then()
                .statusCode(400);
    }

    @Test
    void getWorkItems_byLabelPattern_returnsMatching() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Label query test 55",
                          "createdBy": "alice",
                          "labels": [{"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"}]
                        }
                        """)
                .post("/workitems")
                .then().statusCode(201);

        given()
                .queryParam("label", "legal/contracts")
                .get("/workitems")
                .then()
                .statusCode(200)
                .body("title", org.hamcrest.Matchers.hasItem("Label query test 55"));

        given()
                .queryParam("label", "legal/**")
                .get("/workitems")
                .then()
                .statusCode(200)
                .body("title", org.hamcrest.Matchers.hasItem("Label query test 55"));
    }

    @Test
    void addManualLabel_toExistingWorkItem_appearsInResponse() {
        var id = given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"Add label test 55\", \"createdBy\": \"alice\"}")
                .post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("{\"path\": \"legal/contracts\", \"appliedBy\": \"alice\"}")
                .post("/workitems/" + id + "/labels")
                .then()
                .statusCode(200)
                .body("labels.path", org.hamcrest.Matchers.hasItem("legal/contracts"));
    }

    @Test
    void removeManualLabel_fromWorkItem_disappearsFromResponse() {
        var id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Remove label test 55",
                          "createdBy": "alice",
                          "labels": [{"path": "legal/contracts", "persistence": "MANUAL", "appliedBy": "alice"}]
                        }
                        """)
                .post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .queryParam("path", "legal/contracts")
                .delete("/workitems/" + id + "/labels")
                .then()
                .statusCode(200)
                .body("labels", hasSize(0));
    }

    @Test
    void removeNonExistentLabel_returns404() {
        var id = given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"404 label test\", \"createdBy\": \"alice\"}")
                .post("/workitems")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .queryParam("path", "nonexistent/label")
                .delete("/workitems/" + id + "/labels")
                .then()
                .statusCode(404);
    }
}

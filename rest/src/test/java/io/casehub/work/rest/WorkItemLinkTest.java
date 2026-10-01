package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.casehub.work.rest.test.WorkItemTestFixture;
import io.restassured.http.ContentType;

/**
 * Integration and E2E tests for WorkItemLink.
 *
 * <p>
 * WorkItemLinks reference external resources (design specs, policies, evidence,
 * S3 files stored elsewhere). The relationType is a pluggable string — not an enum.
 */
@QuarkusTest
class WorkItemLinkTest {

    // ── POST /workitems/{id}/links ────────────────────────────────────────────

    @Test
    void addLink_returns201_withAllFields() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://docs.example.com/design-spec-v2.pdf\"," +
                        "\"title\":\"Design Spec v2\",\"relationType\":\"design-spec\",\"linkedBy\":\"alice\"}")
                .post("/api/work/links/add-link/" + itemId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("url", equalTo("https://docs.example.com/design-spec-v2.pdf"))
                .body("title", equalTo("Design Spec v2"))
                .body("relationType", equalTo("design-spec"))
                .body("createdAt", notNullValue());
    }

    @Test
    void addLink_acceptsBlankUrl() {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"\",\"title\":\"Empty\",\"relationType\":\"reference\"}")
                .post("/api/work/links/add-link/" + createWorkItem())
                .then().statusCode(201);
    }

    @Test
    void addLink_acceptsBlankRelationType() {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com\",\"title\":\"T\",\"relationType\":\"\"}")
                .post("/api/work/links/add-link/" + createWorkItem())
                .then().statusCode(201);
    }

    @Test
    void addLink_acceptsCustomRelationType_withoutRegistration() {
        final String itemId = createWorkItem();
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://wiki.internal/page\",\"title\":\"Wiki\",\"relationType\":\"internal-wiki\",\"linkedBy\":\"bob\"}")
                .post("/api/work/links/add-link/" + itemId)
                .then().statusCode(201)
                .body("relationType", equalTo("internal-wiki"));
    }

    @Test
    void addLink_titleIsOptional() {
        final String itemId = createWorkItem();
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://example.com/doc\",\"relationType\":\"reference\",\"linkedBy\":\"alice\"}")
                .post("/api/work/links/add-link/" + itemId)
                .then().statusCode(201)
                .body("url", equalTo("https://example.com/doc"));
    }

    // ── GET /workitems/{id}/links ─────────────────────────────────────────────

    @Test
    void listLinks_returnsEmpty_forNewWorkItem() {
        given().get("/api/work/links/list-links/" + createWorkItem())
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void listLinks_returnsAllLinks_chronologically() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://a.example.com", "design-spec");
        addLink(itemId, "https://b.example.com", "policy");

        given().get("/api/work/links/list-links/" + itemId)
                .then().statusCode(200).body("$", hasSize(2));
    }

    @Test
    void listLinks_returnsAllTypes() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://spec.example.com", "design-spec");
        addLink(itemId, "https://policy.example.com", "policy");
        addLink(itemId, "https://ref.example.com", "design-spec");

        given().get("/api/work/links/list-links/" + itemId)
                .then().statusCode(200)
                .body("$", hasSize(3))
                .body("relationType", hasItem("design-spec"))
                .body("relationType", hasItem("policy"));
    }

    @Test
    void listLinks_isolatedAcrossWorkItems() {
        final String item1 = createWorkItem();
        final String item2 = createWorkItem();
        addLink(item1, "https://example.com", "reference");

        given().get("/api/work/links/list-links/" + item2)
                .then().statusCode(200).body("$", empty());
    }

    // ── DELETE /workitems/{id}/links/{linkId} ─────────────────────────────────

    @Test
    void deleteLink_returns204_andLinkIsGone() {
        final String itemId = createWorkItem();
        final String linkId = given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://delete.me\",\"relationType\":\"reference\",\"linkedBy\":\"alice\"}")
                .post("/api/work/links/add-link/" + itemId)
                .then().statusCode(201).extract().path("id");

        given().post("/api/work/links/delete-link/" + itemId + "/" + linkId)
                .then().statusCode(204);

        given().get("/api/work/links/list-links/" + itemId)
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteLink_returns204_forUnknownLink() {
        given().post("/api/work/links/delete-link/" + createWorkItem() + "/00000000-0000-0000-0000-000000000000")
                .then().statusCode(204);
    }

    @Test
    void deleteLink_onlyRemovesTargetLink() {
        final String itemId = createWorkItem();
        addLink(itemId, "https://keep.example.com", "reference");
        final String removeId = given().contentType(ContentType.JSON)
                .body("{\"url\":\"https://remove.example.com\",\"relationType\":\"policy\",\"linkedBy\":\"alice\"}")
                .post("/api/work/links/add-link/" + itemId)
                .then().statusCode(201).extract().path("id");

        given().post("/api/work/links/delete-link/" + itemId + "/" + removeId).then().statusCode(204);

        given().get("/api/work/links/list-links/" + itemId)
                .then().statusCode(200).body("$", hasSize(1))
                .body("[0].url", equalTo("https://keep.example.com"));
    }

    // ── E2E: design spec + policy + evidence on one WorkItem ─────────────────

    @Test
    void e2e_multipleTypes_allReturnedTogether() {
        final String itemId = createWorkItem();

        addLink(itemId, "https://confluence.example.com/design-v3", "design-spec");
        addLink(itemId, "https://gov.uk/gdpr-article-22", "policy");
        addLink(itemId, "https://s3.example.com/model-output-v1.json", "evidence");

        given().get("/api/work/links/list-links/" + itemId)
                .then().statusCode(200)
                .body("$", hasSize(3))
                .body("relationType", hasItem("design-spec"))
                .body("relationType", hasItem("policy"))
                .body("relationType", hasItem("evidence"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Link test item\",\"createdBy\":\"test\"}")
                .post("/api/work/items/create").then().statusCode(201).extract().path("id");
    }

    private void addLink(final String itemId, final String url, final String relationType) {
        given().contentType(ContentType.JSON)
                .body("{\"url\":\"" + url + "\",\"relationType\":\"" + relationType + "\",\"linkedBy\":\"test\"}")
                .post("/api/work/links/add-link/" + itemId).then().statusCode(201);
    }
}

package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.casehub.work.rest.test.WorkItemTestFixture;
import io.restassured.http.ContentType;

/**
 * Integration tests for WorkItemNote endpoints.
 *
 * <p>
 * Notes are internal operational annotations — distinct from the immutable
 * structured audit log and from external issue tracker comments. They capture
 * the "why" of operational decisions: why was this delegated, what was found
 * during review, what context the next assignee needs.
 */
@QuarkusTest
class WorkItemNoteTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem() {
        return given().contentType(ContentType.JSON)
                .body("{\"title\":\"Note test item\",\"createdBy\":\"system\"}")
                .post("/api/work/items/create")
                .then().statusCode(201)
                .extract().path("id");
    }

    private String addNote(final String itemId, final String content, final String author) {
        return given().contentType(ContentType.JSON)
                .body("{\"content\":\"" + content + "\",\"author\":\"" + author + "\"}")
                .post("/api/work/notes/add-note/" + itemId)
                .then().statusCode(201)
                .extract().path("id");
    }

    // ── POST /workitems/{id}/notes ────────────────────────────────────────────

    @Test
    void addNote_returns201_withIdAndTimestamp() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"Delegated to Carol — Alice is on leave\",\"author\":\"alice\"}")
                .post("/api/work/notes/add-note/" + itemId)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("content", equalTo("Delegated to Carol — Alice is on leave"))
                .body("author", notNullValue())
                .body("createdAt", notNullValue())
                .body("editedAt", nullValue());
    }

    @Test
    void addNote_acceptsBlankContent() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"\"}")
                .post("/api/work/notes/add-note/" + itemId)
                .then()
                .statusCode(201);
    }

    @Test
    void addNote_acceptsMissingAuthor_setByPrincipal() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"some note\"}")
                .post("/api/work/notes/add-note/" + itemId)
                .then()
                .statusCode(201);
    }

    @Test
    void addNote_multipleNotes_allPersist() {
        final String itemId = createWorkItem();

        addNote(itemId, "First note", "alice");
        addNote(itemId, "Second note", "bob");

        given().get("/api/work/notes/list-notes/" + itemId)
                .then().statusCode(200)
                .body("$", hasSize(2));
    }

    // ── GET /workitems/{id}/notes ─────────────────────────────────────────────

    @Test
    void listNotes_returnsEmpty_forNewWorkItem() {
        final String itemId = createWorkItem();

        given().get("/api/work/notes/list-notes/" + itemId)
                .then().statusCode(200)
                .body("$", empty());
    }

    @Test
    void listNotes_returnsChronological_oldestFirst() {
        final String itemId = createWorkItem();

        addNote(itemId, "First", "alice");
        addNote(itemId, "Second", "bob");

        given().get("/api/work/notes/list-notes/" + itemId)
                .then().statusCode(200)
                .body("[0].content", equalTo("First"))
                .body("[1].content", equalTo("Second"));
    }

    @Test
    void listNotes_isolatedAcrossWorkItems() {
        final String item1 = createWorkItem();
        final String item2 = createWorkItem();

        addNote(item1, "Note on item 1", "alice");

        given().get("/api/work/notes/list-notes/" + item2)
                .then().statusCode(200)
                .body("$", empty());
    }

    // ── PUT /workitems/{id}/notes/{noteId} ────────────────────────────────────

    @Test
    void editNote_updatesContent_setsEditedAt() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "Original content", "alice");

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"Revised content — found additional context\"}")
                .post("/api/work/notes/edit-note/" + itemId + "/" + noteId)
                .then()
                .statusCode(200)
                .body("content", equalTo("Revised content — found additional context"))
                .body("editedAt", notNullValue());
    }

    @Test
    void editNote_returns500_forUnknownNote() {
        final String itemId = createWorkItem();

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"irrelevant\"}")
                .post("/api/work/notes/edit-note/" + itemId + "/" + java.util.UUID.randomUUID())
                .then()
                .statusCode(500);
    }

    @Test
    void editNote_acceptsBlankContent() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "Original", "alice");

        given().contentType(ContentType.JSON)
                .body("{\"content\":\"\"}")
                .post("/api/work/notes/edit-note/" + itemId + "/" + noteId)
                .then()
                .statusCode(200);
    }

    // ── DELETE /workitems/{id}/notes/{noteId} ─────────────────────────────────

    @Test
    void deleteNote_returns204_andNoteIsGone() {
        final String itemId = createWorkItem();
        final String noteId = addNote(itemId, "To be deleted", "alice");

        given().post("/api/work/notes/delete-note/" + itemId + "/" + noteId)
                .then().statusCode(204);

        given().get("/api/work/notes/list-notes/" + itemId)
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteNote_returns204_forUnknownNote() {
        final String itemId = createWorkItem();

        given().post("/api/work/notes/delete-note/" + itemId + "/" + java.util.UUID.randomUUID())
                .then().statusCode(204);
    }

    @Test
    void deleteNote_onlyRemovesTargetNote() {
        final String itemId = createWorkItem();
        final String note1 = addNote(itemId, "Keep this", "alice");
        final String note2 = addNote(itemId, "Delete this", "bob");

        given().post("/api/work/notes/delete-note/" + itemId + "/" + note2)
                .then().statusCode(204);

        given().get("/api/work/notes/list-notes/" + itemId)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(note1));
    }
}

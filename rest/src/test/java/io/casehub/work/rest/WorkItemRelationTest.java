package io.casehub.work.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.casehub.work.rest.test.WorkItemTestFixture;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * Integration and end-to-end tests for WorkItem relation graph.
 *
 * <h2>What relations enable in UIs</h2>
 * <ul>
 * <li>PART_OF → tree navigation, breadcrumb trails, group summaries</li>
 * <li>Filtering: "show only top-level" = WorkItems with no outgoing PART_OF</li>
 * <li>Group progress: X of Y children COMPLETED</li>
 * <li>Any custom type (TRIGGERED_BY, APPROVED_BY) — no registration required</li>
 * </ul>
 *
 * <h2>Test tiers</h2>
 * <ul>
 * <li><strong>Unit</strong> — WorkItemRelationTypeTest (pure Java, relation type constants)</li>
 * <li><strong>Integration</strong> — CRUD, idempotency, isolation</li>
 * <li><strong>Happy path</strong> — build a tree, navigate parent/children</li>
 * <li><strong>E2E</strong> — cycle prevention, custom types, incoming relations</li>
 * </ul>
 */
@QuarkusTest
class WorkItemRelationTest {

    // ── POST /workitems/{id}/relations ────────────────────────────────────────

    @Test
    void addRelation_returns201_withAllFields() {
        final String child = createWorkItem("Child task");
        final String parent = createWorkItem("Parent epic");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + child)
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("sourceId", equalTo(child))
                .body("targetId", equalTo(parent))
                .body("relationType", equalTo("PART_OF"))
                .body("createdAt", notNullValue());
    }

    @Test
    void addRelation_returns500_whenTargetIdMissing() {
        final String id = createWorkItem("Item");
        given().contentType(ContentType.JSON)
                .body("{\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + id)
                .then().statusCode(500);
    }

    @Test
    void addRelation_returns500_whenRelationTypeMissing() {
        final String id = createWorkItem("Item");
        final String other = createWorkItem("Other");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + other + "\"}")
                .post("/api/work/relations/add-relation/" + id)
                .then().statusCode(500);
    }

    @Test
    void addRelation_acceptsCustomType_withoutRegistration() {
        final String source = createWorkItem("Trigger");
        final String target = createWorkItem("Target");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + target + "\",\"relationType\":\"TRIGGERED_BY\"}")
                .post("/api/work/relations/add-relation/" + source)
                .then()
                .statusCode(201)
                .body("relationType", equalTo("TRIGGERED_BY"));
    }

    @Test
    void addRelation_isIdempotent_secondAddReturns500() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");
        final String body = "{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}";

        given().contentType(ContentType.JSON).body(body)
                .post("/api/work/relations/add-relation/" + child).then().statusCode(201);

        given().contentType(ContentType.JSON).body(body)
                .post("/api/work/relations/add-relation/" + child).then().statusCode(500);
    }

    // ── GET /workitems/{id}/relations (outgoing) ──────────────────────────────

    @Test
    void listRelations_returnsEmpty_forNewWorkItem() {
        given().get("/api/work/relations/list-outgoing/" + createWorkItem("Isolated"))
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void listRelations_returnsOutgoingRelations() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + child).then().statusCode(201);

        given().get("/api/work/relations/list-outgoing/" + child)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].relationType", equalTo("PART_OF"))
                .body("[0].targetId", equalTo(parent));
    }

    // ── GET /workitems/{id}/relations/incoming ────────────────────────────────

    @Test
    void listIncomingRelations_returnsRelationsTargetingThisItem() {
        final String parent = createWorkItem("Epic parent");
        final String child1 = createWorkItem("Child 1");
        final String child2 = createWorkItem("Child 2");

        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + child1).then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + child2).then().statusCode(201);

        given().get("/api/work/relations/list-incoming/" + parent)
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("sourceId", hasItem(child1))
                .body("sourceId", hasItem(child2));
    }

    // ── GET /workitems/{id}/children (convenience) ────────────────────────────

    @Test
    void children_returnsPartOfIncomingItems() {
        final String parent = createWorkItem("Group");
        final String child1 = createWorkItem("Sub-task 1");
        final String child2 = createWorkItem("Sub-task 2");
        final String unrelated = createWorkItem("Unrelated");

        addPartOf(child1, parent);
        addPartOf(child2, parent);
        // unrelated has no PART_OF relation to parent

        given().get("/api/work/relations/children/" + parent)
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("id", hasItem(child1))
                .body("id", hasItem(child2));
    }

    @Test
    void children_returnsEmpty_forLeafNode() {
        given().get("/api/work/relations/children/" + createWorkItem("Leaf"))
                .then().statusCode(200).body("$", empty());
    }

    // ── GET /workitems/{id}/parent (convenience) ──────────────────────────────

    @Test
    void parent_returnsParentWorkItem_viaPartOf() {
        final String child = createWorkItem("Child task");
        final String parent = createWorkItem("Parent epic");
        addPartOf(child, parent);

        given().get("/api/work/relations/parent/" + child)
                .then().statusCode(200)
                .body("id", equalTo(parent));
    }

    @Test
    void parent_returns404_whenNoPartOfRelation() {
        given().get("/api/work/relations/parent/" + createWorkItem("Root"))
                .then().statusCode(404);
    }

    // ── DELETE /workitems/{id}/relations/{relationId} ─────────────────────────

    @Test
    void deleteRelation_returns204_andRelationIsGone() {
        final String child = createWorkItem("Child");
        final String parent = createWorkItem("Parent");

        final String relationId = given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parent + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + child)
                .then().statusCode(201).extract().path("id");

        given().post("/api/work/relations/delete-relation/" + child + "/" + relationId)
                .then().statusCode(204);

        given().get("/api/work/relations/list-outgoing/" + child)
                .then().statusCode(200).body("$", empty());
    }

    @Test
    void deleteRelation_returns204_forUnknownRelation() {
        given().post("/api/work/relations/delete-relation/" + createWorkItem("Item") + "/00000000-0000-0000-0000-000000000000")
                .then().statusCode(204);
    }

    // ── E2E: cycle prevention for PART_OF ────────────────────────────────────

    @Test
    void addRelation_returns500_whenPartOfCreatesDirectCycle() {
        final String a = createWorkItem("A");
        final String b = createWorkItem("B");

        addPartOf(a, b); // A is child of B

        // B PART_OF A would create a cycle
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + b)
                .then().statusCode(500);
    }

    @Test
    void addRelation_returns500_whenPartOfCreatesIndirectCycle() {
        final String a = createWorkItem("A");
        final String b = createWorkItem("B");
        final String c = createWorkItem("C");

        addPartOf(a, b); // A → B
        addPartOf(b, c); // B → C

        // C PART_OF A would create cycle: A → B → C → A
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + c)
                .then().statusCode(500);
    }

    @Test
    void addRelation_returns500_whenPartOfSelf() {
        final String id = createWorkItem("Self-referencing");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + id + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + id)
                .then().statusCode(500);
    }

    @Test
    void cycleCheck_appliesToSelfLoop_evenForNonPartOf() {
        final String a = createWorkItem("A");
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + a + "\",\"relationType\":\"RELATES_TO\"}")
                .post("/api/work/relations/add-relation/" + a)
                .then().statusCode(500);
    }

    // ── E2E: tree navigation (happy path) ────────────────────────────────────

    @Test
    void e2e_buildTree_navigateUpAndDown() {
        final String root = createWorkItem("Q2 Security Review (Epic)");
        final String child1 = createWorkItem("Threat modelling");
        final String child2 = createWorkItem("Pen test coordination");
        final String grandchild = createWorkItem("Review OWASP findings");

        addPartOf(child1, root);
        addPartOf(child2, root);
        addPartOf(grandchild, child2);

        // Navigate down: root has 2 direct children
        given().get("/api/work/relations/children/" + root)
                .then().statusCode(200).body("$", hasSize(2));

        // Navigate down further: child2 has 1 child
        given().get("/api/work/relations/children/" + child2)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(grandchild));

        // Navigate up: grandchild's parent is child2
        given().get("/api/work/relations/parent/" + grandchild)
                .then().statusCode(200).body("id", equalTo(child2));

        // Navigate up again: child2's parent is root
        given().get("/api/work/relations/parent/" + child2)
                .then().statusCode(200).body("id", equalTo(root));

        // Root has no parent
        given().get("/api/work/relations/parent/" + root)
                .then().statusCode(404);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String createWorkItem(final String title) {
        return WorkItemTestFixture.createWorkItem("{\"title\":\"" + title + "\"}");
    }

    private void addPartOf(final String childId, final String parentId) {
        given().contentType(ContentType.JSON)
                .body("{\"targetId\":\"" + parentId + "\",\"relationType\":\"PART_OF\"}")
                .post("/api/work/relations/add-relation/" + childId)
                .then().statusCode(201);
    }
}

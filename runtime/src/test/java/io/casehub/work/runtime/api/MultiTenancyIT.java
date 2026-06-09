package io.casehub.work.runtime.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end multi-tenant integration tests verifying tenant isolation across
 * the REST API layer.
 *
 * <p>Uses {@link MutableCurrentPrincipal} to switch tenant context between
 * HTTP calls. Each test creates data under one tenant and verifies it is
 * invisible to another tenant — 404, not 403.
 *
 * <p>These tests exercise the full stack: REST → Service → Store → JPA,
 * confirming that tenant filtering is applied consistently at every layer.
 *
 * <p>Issue #256.
 */
@QuarkusTest
class MultiTenancyIT {

    private static final String TENANT_A = "mt-tenant-a";
    private static final String TENANT_B = "mt-tenant-b";

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    // ── Scenario 1: WorkItem isolation ───────────────────────────────────────

    @Test
    void workItem_createdByTenantA_isInvisibleToTenantB() {
        // Create WorkItem as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Tenant A item");

        // GET as tenant A → 200
        given().get("/workitems/{id}", id)
                .then().statusCode(200)
                .body("id", equalTo(id));

        // Switch to tenant B → 404 (not 403)
        principal.setTenancyId(TENANT_B);
        given().get("/workitems/{id}", id)
                .then().statusCode(404);
    }

    @Test
    void workItem_listAll_onlyReturnsSameTenantItems() {
        // Create item in tenant A
        principal.setTenancyId(TENANT_A);
        final String idA = createWorkItem("Tenant A list item");

        // Create item in tenant B
        principal.setTenancyId(TENANT_B);
        final String idB = createWorkItem("Tenant B list item");

        // List as tenant A → contains A, not B
        principal.setTenancyId(TENANT_A);
        final List<String> idsA = given().get("/workitems")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(idsA).contains(idA);
        assertThat(idsA).doesNotContain(idB);

        // List as tenant B → contains B, not A
        principal.setTenancyId(TENANT_B);
        final List<String> idsB = given().get("/workitems")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(idsB).contains(idB);
        assertThat(idsB).doesNotContain(idA);
    }

    // ── Scenario 2: Lifecycle isolation ──────────────────────────────────────

    @Test
    void lifecycle_completedByTenantA_auditInvisibleToTenantB() {
        // Full lifecycle as tenant A
        principal.setTenancyId(TENANT_A);
        final String actor = "mt-alice-" + System.nanoTime();
        final String id = createWorkItem("Lifecycle isolation item");
        given().queryParam("claimant", actor)
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200);
        given().queryParam("actor", actor)
                .put("/workitems/{id}/start", id)
                .then().statusCode(200);
        given().contentType(ContentType.JSON)
                .queryParam("actor", actor)
                .body("{\"resolution\":\"done\"}")
                .put("/workitems/{id}/complete", id)
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"));

        // Verify audit trail exists as tenant A
        given().queryParam("actorId", actor)
                .get("/audit")
                .then().statusCode(200)
                .body("entries.size()", greaterThanOrEqualTo(1));

        // Switch to tenant B → audit query returns empty
        principal.setTenancyId(TENANT_B);
        given().queryParam("actorId", actor)
                .get("/audit")
                .then().statusCode(200)
                .body("entries", empty())
                .body("total", equalTo(0));
    }

    @Test
    void lifecycle_getById_afterLifecycle_otherTenantSees404() {
        // Create + claim as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant lifecycle check");
        given().queryParam("claimant", "mt-bob")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200);

        // Tenant A can see the ASSIGNED item
        given().get("/workitems/{id}", id)
                .then().statusCode(200)
                .body("status", equalTo("ASSIGNED"));

        // Tenant B cannot see it at all
        principal.setTenancyId(TENANT_B);
        given().get("/workitems/{id}", id)
                .then().statusCode(404);
    }

    // ── Scenario 3: Template isolation ───────────────────────────────────────

    @Test
    void template_sameNameInBothTenants_noConflict() {
        final String templateName = "mt-review-" + System.nanoTime();

        // Create template "review" as tenant A → 201
        principal.setTenancyId(TENANT_A);
        final String templateIdA = given().contentType(ContentType.JSON)
                .body("{\"name\":\"" + templateName + "\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .body("name", equalTo(templateName))
                .extract().path("id");

        // Create template with same name as tenant B → 201 (not 409)
        principal.setTenancyId(TENANT_B);
        final String templateIdB = given().contentType(ContentType.JSON)
                .body("{\"name\":\"" + templateName + "\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .body("name", equalTo(templateName))
                .extract().path("id");

        // IDs must be different
        assertThat(templateIdA).isNotEqualTo(templateIdB);

        // List as tenant A → sees only tenant A's template
        principal.setTenancyId(TENANT_A);
        final List<String> namesA = given().get("/workitem-templates")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(namesA).contains(templateIdA);
        assertThat(namesA).doesNotContain(templateIdB);

        // List as tenant B → sees only tenant B's template
        principal.setTenancyId(TENANT_B);
        final List<String> namesB = given().get("/workitem-templates")
                .then().statusCode(200)
                .extract().jsonPath().getList("id");
        assertThat(namesB).contains(templateIdB);
        assertThat(namesB).doesNotContain(templateIdA);
    }

    @Test
    void template_getById_crossTenant_returns404() {
        principal.setTenancyId(TENANT_A);
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"mt-cross-check-" + System.nanoTime() + "\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        // Tenant A can see it
        given().get("/workitem-templates/{id}", templateId)
                .then().statusCode(200)
                .body("id", equalTo(templateId));

        // Tenant B cannot
        principal.setTenancyId(TENANT_B);
        given().get("/workitem-templates/{id}", templateId)
                .then().statusCode(404);
    }

    // ── Scenario 4: Cross-tenant prevention — lifecycle operations ───────────

    @Test
    void crossTenantPrevention_claimByOtherTenant_returns404() {
        // Create WorkItem as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant claim prevention");

        // Attempt claim as tenant B → 404 (item not found in tenant B's scope)
        principal.setTenancyId(TENANT_B);
        given().queryParam("claimant", "mt-mallory")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(404);

        // Original tenant A can still claim it
        principal.setTenancyId(TENANT_A);
        given().queryParam("claimant", "mt-alice")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200)
                .body("status", equalTo("ASSIGNED"));
    }

    @Test
    void crossTenantPrevention_startByOtherTenant_returns404() {
        // Create + claim as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant start prevention");
        given().queryParam("claimant", "mt-alice")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200);

        // Attempt start as tenant B → 404
        principal.setTenancyId(TENANT_B);
        given().queryParam("actor", "mt-alice")
                .put("/workitems/{id}/start", id)
                .then().statusCode(404);
    }

    @Test
    void crossTenantPrevention_completeByOtherTenant_returns404() {
        // Create + claim + start as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant complete prevention");
        given().queryParam("claimant", "mt-alice")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200);
        given().queryParam("actor", "mt-alice")
                .put("/workitems/{id}/start", id)
                .then().statusCode(200);

        // Attempt complete as tenant B → 404
        principal.setTenancyId(TENANT_B);
        given().contentType(ContentType.JSON)
                .queryParam("actor", "mt-alice")
                .body("{\"resolution\":\"stolen\"}")
                .put("/workitems/{id}/complete", id)
                .then().statusCode(404);

        // Original tenant A can complete
        principal.setTenancyId(TENANT_A);
        given().contentType(ContentType.JSON)
                .queryParam("actor", "mt-alice")
                .body("{\"resolution\":\"done\"}")
                .put("/workitems/{id}/complete", id)
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void crossTenantPrevention_cancelByOtherTenant_returns404() {
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant cancel prevention");

        // Attempt cancel as tenant B → 404
        principal.setTenancyId(TENANT_B);
        given().contentType(ContentType.JSON)
                .queryParam("actor", "mt-admin")
                .body("{\"reason\":\"malicious cancel\"}")
                .put("/workitems/{id}/cancel", id)
                .then().statusCode(404);
    }

    @Test
    void crossTenantPrevention_delegateByOtherTenant_returns404() {
        // Create + claim as tenant A
        principal.setTenancyId(TENANT_A);
        final String id = createWorkItem("Cross-tenant delegate prevention");
        given().queryParam("claimant", "mt-alice")
                .put("/workitems/{id}/claim", id)
                .then().statusCode(200);

        // Attempt delegate as tenant B → 404
        principal.setTenancyId(TENANT_B);
        given().contentType(ContentType.JSON)
                .queryParam("actor", "mt-alice")
                .body("{\"to\":\"mt-mallory\"}")
                .put("/workitems/{id}/delegate", id)
                .then().statusCode(404);
    }

    @Test
    void crossTenantPrevention_templateDeleteByOtherTenant_returns404() {
        // Create template as tenant A
        principal.setTenancyId(TENANT_A);
        final String templateId = given().contentType(ContentType.JSON)
                .body("{\"name\":\"mt-delete-guard-" + System.nanoTime() + "\",\"createdBy\":\"admin\"}")
                .post("/workitem-templates")
                .then().statusCode(201)
                .extract().path("id");

        // Attempt delete as tenant B → 404
        principal.setTenancyId(TENANT_B);
        given().delete("/workitem-templates/{id}", templateId)
                .then().statusCode(404);

        // Tenant A can still delete
        principal.setTenancyId(TENANT_A);
        given().delete("/workitem-templates/{id}", templateId)
                .then().statusCode(204);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String createWorkItem(final String title) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "%s",
                          "priority": "MEDIUM",
                          "createdBy": "mt-system"
                        }
                        """.formatted(title))
                .post("/workitems")
                .then().statusCode(201)
                .body("id", notNullValue())
                .extract().path("id");
    }
}

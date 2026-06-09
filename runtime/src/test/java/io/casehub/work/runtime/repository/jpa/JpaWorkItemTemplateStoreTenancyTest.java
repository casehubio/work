package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemTemplateStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemTemplateStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemTemplateStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkItemTemplate newTemplate(String name) {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.createdBy = "test";
        t.createdAt = Instant.now();
        return t;
    }

    // -------------------------------------------------------------------------
    // put() stamps tenancyId
    // -------------------------------------------------------------------------

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        WorkItemTemplate t = newTemplate("stamp-test");
        assertThat(t.tenancyId).isNull();

        store.put(t);

        assertThat(t.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void put_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);

        WorkItemTemplate t = newTemplate("preserve-test");
        t.tenancyId = TENANT_A; // explicitly set to A

        store.put(t);

        // Should keep A, not overwrite with B
        assertThat(t.tenancyId).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // get() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_forAnotherTenantTemplate() {
        // Create template as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate t = newTemplate("get-isolation");
        store.put(t);
        UUID id = t.id;

        // Switch to tenant B — should not see A's template
        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        // Switch back to A — should see it
        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    // -------------------------------------------------------------------------
    // getByName() tenant isolation + name uniqueness is per-tenant
    // -------------------------------------------------------------------------

    @Test
    void getByName_returnsEmpty_forAnotherTenantTemplate() {
        String name = "shared-name-" + UUID.randomUUID();

        // Create template as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate tA = newTemplate(name);
        store.put(tA);

        // Switch to tenant B — should not find by name
        principal.setTenancyId(TENANT_B);
        assertThat(store.getByName(name)).isEmpty();

        // Switch back to A — should find it
        principal.setTenancyId(TENANT_A);
        assertThat(store.getByName(name)).isPresent();
    }

    @Test
    void twoTenants_canCreateTemplatesWithSameName() {
        String name = "duplicate-name-" + UUID.randomUUID();

        // Create template as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate tA = newTemplate(name);
        store.put(tA);

        // Create template with same name as tenant B — should succeed
        principal.setTenancyId(TENANT_B);
        WorkItemTemplate tB = newTemplate(name);
        store.put(tB);

        // Both should exist, each scoped to their tenant
        assertThat(store.getByName(name)).isPresent();
        assertThat(store.getByName(name).get().id).isEqualTo(tB.id);

        principal.setTenancyId(TENANT_A);
        assertThat(store.getByName(name)).isPresent();
        assertThat(store.getByName(name).get().id).isEqualTo(tA.id);
    }

    // -------------------------------------------------------------------------
    // scanAll() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void scanAll_returnsOnlyCurrentTenantTemplates() {
        String nameA = "scan-tenant-a-" + UUID.randomUUID();
        String nameB = "scan-tenant-b-" + UUID.randomUUID();

        // Create template for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate tA = newTemplate(nameA);
        store.put(tA);

        // Create template for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItemTemplate tB = newTemplate(nameB);
        store.put(tB);

        // As tenant B, should only see B's template
        List<WorkItemTemplate> resultB = store.scanAll();
        assertThat(resultB).extracting("name").contains(nameB);
        assertThat(resultB).extracting("name").doesNotContain(nameA);

        // As tenant A, should only see A's template
        principal.setTenancyId(TENANT_A);
        List<WorkItemTemplate> resultA = store.scanAll();
        assertThat(resultA).extracting("name").contains(nameA);
        assertThat(resultA).extracting("name").doesNotContain(nameB);
    }

    // -------------------------------------------------------------------------
    // delete() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void delete_cannotDeleteAnotherTenantTemplate() {
        // Create template as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemTemplate t = newTemplate("delete-isolation");
        store.put(t);
        UUID id = t.id;

        // Switch to tenant B — delete should fail (returns false)
        principal.setTenancyId(TENANT_B);
        boolean deleted = store.delete(id);
        assertThat(deleted).isFalse();

        // Switch back to A — template should still exist
        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();

        // Delete as A — should succeed
        deleted = store.delete(id);
        assertThat(deleted).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}

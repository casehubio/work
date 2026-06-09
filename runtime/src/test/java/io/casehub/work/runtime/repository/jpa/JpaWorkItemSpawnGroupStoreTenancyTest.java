package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemSpawnGroupStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemSpawnGroupStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemSpawnGroupStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private WorkItemSpawnGroup newGroup(UUID parentId, String key) {
        WorkItemSpawnGroup g = new WorkItemSpawnGroup();
        g.parentId = parentId;
        g.idempotencyKey = key;
        g.createdAt = Instant.now();
        return g;
    }

    private WorkItemSpawnGroup newMultiInstanceGroup(UUID parentId, String key, int instanceCount, int requiredCount) {
        WorkItemSpawnGroup g = newGroup(parentId, key);
        g.instanceCount = instanceCount;
        g.requiredCount = requiredCount;
        return g;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        WorkItemSpawnGroup g = newGroup(UUID.randomUUID(), "key-1");
        assertThat(g.tenancyId).isNull();

        store.put(g);

        assertThat(g.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantGroup() {
        principal.setTenancyId(TENANT_A);
        WorkItemSpawnGroup g = newGroup(UUID.randomUUID(), "key-1");
        store.put(g);
        UUID id = g.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findByParentId_returnsOnlyCurrentTenantGroups() {
        UUID parentId = UUID.randomUUID();

        principal.setTenancyId(TENANT_A);
        store.put(newGroup(parentId, "a-key"));

        principal.setTenancyId(TENANT_B);
        store.put(newGroup(parentId, "b-key"));

        List<WorkItemSpawnGroup> resultB = store.findByParentId(parentId);
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).idempotencyKey).isEqualTo("b-key");

        principal.setTenancyId(TENANT_A);
        List<WorkItemSpawnGroup> resultA = store.findByParentId(parentId);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).idempotencyKey).isEqualTo("a-key");
    }

    @Test
    void findByParentAndKey_tenantIsolated() {
        UUID parentId = UUID.randomUUID();
        String key = "shared-key";

        principal.setTenancyId(TENANT_A);
        store.put(newGroup(parentId, key));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByParentAndKey(parentId, key)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByParentAndKey(parentId, key)).isPresent();
    }

    @Test
    void findMultiInstanceByParentId_tenantIsolated() {
        UUID parentId = UUID.randomUUID();

        principal.setTenancyId(TENANT_A);
        store.put(newMultiInstanceGroup(parentId, "mi-key", 5, 3));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findMultiInstanceByParentId(parentId)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findMultiInstanceByParentId(parentId)).isPresent();
    }

    @Test
    void delete_cannotDeleteAnotherTenantGroup() {
        principal.setTenancyId(TENANT_A);
        WorkItemSpawnGroup g = newGroup(UUID.randomUUID(), "del-key");
        store.put(g);
        UUID id = g.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}

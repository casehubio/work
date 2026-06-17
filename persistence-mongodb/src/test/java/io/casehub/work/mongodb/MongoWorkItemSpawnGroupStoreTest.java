package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoWorkItemSpawnGroupStoreTest {

    @Inject
    WorkItemSpawnGroupStore store;

    @Inject
    MutableCurrentPrincipal principal;

    private String tenantA;
    private String tenantB;

    @BeforeEach
    void setUp() {
        principal.reset();
        tenantA = "tenant-" + UUID.randomUUID();
        tenantB = "tenant-" + UUID.randomUUID();

        // Clean up all spawn groups before each test
        MongoWorkItemSpawnGroupDocument.deleteAll();
    }

    @Test
    void put_and_get_roundtrip() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();
        final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = parentId;
        group.idempotencyKey = "spawn-1";
        group.createdAt = now;
        group.instanceCount = 5;
        group.requiredCount = 3;
        group.onThresholdReached = "CANCEL";
        group.allowSameAssignee = true;
        group.parentRole = "COORDINATOR";
        group.completedCount = 1;
        group.rejectedCount = 0;
        group.policyTriggered = false;

        final WorkItemSpawnGroup saved = store.put(group);
        final Optional<WorkItemSpawnGroup> retrieved = store.get(saved.id);

        assertThat(retrieved).isPresent();
        final WorkItemSpawnGroup loaded = retrieved.get();
        assertThat(loaded.id).isEqualTo(saved.id);
        assertThat(loaded.tenancyId).isEqualTo(tenantA);
        assertThat(loaded.parentId).isEqualTo(parentId);
        assertThat(loaded.idempotencyKey).isEqualTo("spawn-1");
        assertThat(loaded.createdAt).isEqualTo(now);
        assertThat(loaded.version).isEqualTo(0L);
        assertThat(loaded.instanceCount).isEqualTo(5);
        assertThat(loaded.requiredCount).isEqualTo(3);
        assertThat(loaded.onThresholdReached).isEqualTo("CANCEL");
        assertThat(loaded.allowSameAssignee).isTrue();
        assertThat(loaded.parentRole).isEqualTo("COORDINATOR");
        assertThat(loaded.completedCount).isEqualTo(1);
        assertThat(loaded.rejectedCount).isEqualTo(0);
        assertThat(loaded.policyTriggered).isFalse();
    }

    @Test
    void findByParentId_orderedByCreatedAtDesc() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();
        final Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // Create three groups with different timestamps
        final WorkItemSpawnGroup g1 = new WorkItemSpawnGroup();
        g1.parentId = parentId;
        g1.idempotencyKey = "group-1";
        g1.createdAt = base;
        store.put(g1);

        final WorkItemSpawnGroup g2 = new WorkItemSpawnGroup();
        g2.parentId = parentId;
        g2.idempotencyKey = "group-2";
        g2.createdAt = base.plus(1, ChronoUnit.HOURS);
        store.put(g2);

        final WorkItemSpawnGroup g3 = new WorkItemSpawnGroup();
        g3.parentId = parentId;
        g3.idempotencyKey = "group-3";
        g3.createdAt = base.plus(2, ChronoUnit.HOURS);
        store.put(g3);

        final List<WorkItemSpawnGroup> groups = store.findByParentId(parentId);

        assertThat(groups).hasSize(3);
        assertThat(groups.get(0).idempotencyKey).isEqualTo("group-3");
        assertThat(groups.get(1).idempotencyKey).isEqualTo("group-2");
        assertThat(groups.get(2).idempotencyKey).isEqualTo("group-1");
    }

    @Test
    void findByParentAndKey_returns_whenExists() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();
        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = parentId;
        group.idempotencyKey = "unique-key";
        store.put(group);

        final Optional<WorkItemSpawnGroup> found = store.findByParentAndKey(parentId, "unique-key");

        assertThat(found).isPresent();
        assertThat(found.get().idempotencyKey).isEqualTo("unique-key");
    }

    @Test
    void findByParentAndKey_empty_whenNotFound() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();
        final Optional<WorkItemSpawnGroup> found = store.findByParentAndKey(parentId, "nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void findMultiInstanceByParentId_returnsGroupWithRequiredCount() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();

        // Group without requiredCount (non-multi-instance)
        final WorkItemSpawnGroup g1 = new WorkItemSpawnGroup();
        g1.parentId = parentId;
        g1.idempotencyKey = "simple-group";
        g1.instanceCount = 3;
        g1.requiredCount = null;
        store.put(g1);

        // Group with requiredCount (multi-instance)
        final WorkItemSpawnGroup g2 = new WorkItemSpawnGroup();
        g2.parentId = parentId;
        g2.idempotencyKey = "multi-instance-group";
        g2.instanceCount = 5;
        g2.requiredCount = 3;
        store.put(g2);

        final Optional<WorkItemSpawnGroup> found = store.findMultiInstanceByParentId(parentId);

        assertThat(found).isPresent();
        assertThat(found.get().idempotencyKey).isEqualTo("multi-instance-group");
        assertThat(found.get().requiredCount).isEqualTo(3);
    }

    @Test
    void findMultiInstanceByParentId_ignoresGroupsWithoutRequiredCount() {
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();

        final WorkItemSpawnGroup g1 = new WorkItemSpawnGroup();
        g1.parentId = parentId;
        g1.idempotencyKey = "simple-group";
        g1.instanceCount = 3;
        g1.requiredCount = null;
        store.put(g1);

        final Optional<WorkItemSpawnGroup> found = store.findMultiInstanceByParentId(parentId);

        assertThat(found).isEmpty();
    }

    @Test
    void put_update_incrementsVersion() {
        principal.setTenancyId(tenantA);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = UUID.randomUUID();
        group.idempotencyKey = "test-group";
        group.completedCount = 0;

        final WorkItemSpawnGroup saved = store.put(group);
        assertThat(saved.version).isEqualTo(0L);

        final Optional<WorkItemSpawnGroup> loaded = store.get(saved.id);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().version).isEqualTo(0L);

        // Update the group
        loaded.get().completedCount = 1;
        final WorkItemSpawnGroup updated = store.put(loaded.get());
        assertThat(updated.version).isEqualTo(1L);

        // Verify persisted version
        final Optional<WorkItemSpawnGroup> reloaded = store.get(saved.id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().version).isEqualTo(1L);
        assertThat(reloaded.get().completedCount).isEqualTo(1);
    }

    @Test
    void put_throwsOptimisticLockException_onStaleVersion() {
        principal.setTenancyId(tenantA);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = UUID.randomUUID();
        group.idempotencyKey = "concurrent-group";
        group.completedCount = 0;

        final WorkItemSpawnGroup saved = store.put(group);

        // Two readers get the same version
        final Optional<WorkItemSpawnGroup> reader1 = store.get(saved.id);
        final Optional<WorkItemSpawnGroup> reader2 = store.get(saved.id);

        assertThat(reader1).isPresent();
        assertThat(reader2).isPresent();
        assertThat(reader1.get().version).isEqualTo(0L);
        assertThat(reader2.get().version).isEqualTo(0L);

        // Reader 1 updates successfully
        reader1.get().completedCount = 1;
        final WorkItemSpawnGroup updated1 = store.put(reader1.get());
        assertThat(updated1.version).isEqualTo(1L);

        // Reader 2 attempts to update with stale version — should fail
        reader2.get().completedCount = 2;
        assertThatThrownBy(() -> store.put(reader2.get()))
                .isInstanceOf(OptimisticLockException.class)
                .hasMessageContaining("Version conflict");
    }

    @Test
    void delete() {
        principal.setTenancyId(tenantA);

        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = UUID.randomUUID();
        group.idempotencyKey = "delete-me";

        final WorkItemSpawnGroup saved = store.put(group);

        // Delete should return true
        final boolean deleted = store.delete(saved.id);
        assertThat(deleted).isTrue();

        // Get should return empty
        final Optional<WorkItemSpawnGroup> retrieved = store.get(saved.id);
        assertThat(retrieved).isEmpty();

        // Second delete should return false
        final boolean deletedAgain = store.delete(saved.id);
        assertThat(deletedAgain).isFalse();
    }

    @Test
    void tenantIsolation() {
        // Create group in tenant A
        principal.setTenancyId(tenantA);

        final UUID parentId = UUID.randomUUID();
        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = parentId;
        group.idempotencyKey = "tenant-a-group";

        final WorkItemSpawnGroup saved = store.put(group);

        // Switch to tenant B — should not see tenant A's group
        principal.setTenancyId(tenantB);

        final Optional<WorkItemSpawnGroup> retrieved = store.get(saved.id);
        assertThat(retrieved).isEmpty();

        final List<WorkItemSpawnGroup> byParent = store.findByParentId(parentId);
        assertThat(byParent).isEmpty();

        final Optional<WorkItemSpawnGroup> byKey = store.findByParentAndKey(parentId, "tenant-a-group");
        assertThat(byKey).isEmpty();

        final Optional<WorkItemSpawnGroup> multiInstance = store.findMultiInstanceByParentId(parentId);
        assertThat(multiInstance).isEmpty();

        final boolean deleted = store.delete(saved.id);
        assertThat(deleted).isFalse();

        // Switch back to tenant A — should still exist
        principal.setTenancyId(tenantA);

        final Optional<WorkItemSpawnGroup> stillThere = store.get(saved.id);
        assertThat(stillThere).isPresent();
    }
}

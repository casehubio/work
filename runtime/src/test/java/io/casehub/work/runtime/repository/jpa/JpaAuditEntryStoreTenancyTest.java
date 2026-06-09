package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaAuditEntryStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaAuditEntryStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    AuditEntryStore store;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private WorkItem newWorkItem(String title) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        return wi;
    }

    private AuditEntry newAuditEntry(UUID workItemId, String event) {
        AuditEntry entry = new AuditEntry();
        entry.workItemId = workItemId;
        entry.event = event;
        entry.actor = "test-actor";
        entry.occurredAt = Instant.now();
        return entry;
    }

    // -------------------------------------------------------------------------
    // append() stamps tenancyId
    // -------------------------------------------------------------------------

    @Test
    void append_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        // Create work item first
        WorkItem wi = newWorkItem("test-workitem");
        workItemStore.put(wi);

        AuditEntry entry = newAuditEntry(wi.id, "CREATED");
        assertThat(entry.tenancyId).isNull();

        store.append(entry);

        assertThat(entry.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void append_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);

        // Create work item first
        WorkItem wi = newWorkItem("test-workitem");
        workItemStore.put(wi);

        AuditEntry entry = newAuditEntry(wi.id, "CREATED");
        entry.tenancyId = TENANT_A; // explicitly set to A

        store.append(entry);

        // Should keep A, not overwrite with B
        assertThat(entry.tenancyId).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // findByWorkItemId() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void findByWorkItemId_returnsEmpty_forAnotherTenantAuditEntry() {
        // Create work item as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("tenant-a-workitem");
        workItemStore.put(wiA);

        // Create audit entry as tenant A
        AuditEntry entryA = newAuditEntry(wiA.id, "CREATED");
        store.append(entryA);

        // Create work item as tenant B (different work item)
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("tenant-b-workitem");
        workItemStore.put(wiB);

        // Create audit entry as tenant B
        AuditEntry entryB = newAuditEntry(wiB.id, "ASSIGNED");
        store.append(entryB);

        // As tenant A — should only see A's entry
        principal.setTenancyId(TENANT_A);
        List<AuditEntry> resultA = store.findByWorkItemId(wiA.id);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).id).isEqualTo(entryA.id);

        // As tenant B — should only see B's entry
        principal.setTenancyId(TENANT_B);
        List<AuditEntry> resultB = store.findByWorkItemId(wiB.id);
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).id).isEqualTo(entryB.id);

        // As tenant A trying to see B's audit — should be empty
        principal.setTenancyId(TENANT_A);
        List<AuditEntry> resultA2 = store.findByWorkItemId(wiB.id);
        assertThat(resultA2).isEmpty();
    }

    // -------------------------------------------------------------------------
    // query() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void query_returnsOnlyCurrentTenantEntries() {
        // Create work item for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("tenant-a-workitem");
        workItemStore.put(wiA);

        // Create audit entries for tenant A
        AuditEntry entryA1 = newAuditEntry(wiA.id, "CREATED");
        entryA1.actor = "alice";
        store.append(entryA1);

        // Create work item for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("tenant-b-workitem");
        workItemStore.put(wiB);

        // Create audit entries for tenant B
        AuditEntry entryB1 = newAuditEntry(wiB.id, "CREATED");
        entryB1.actor = "bob";
        store.append(entryB1);

        // Query as tenant A — should only see A's entry
        principal.setTenancyId(TENANT_A);
        AuditQuery queryA = AuditQuery.builder()
                .actorId("alice")
                .page(0)
                .size(100)
                .build();
        List<AuditEntry> resultA = store.query(queryA);
        assertThat(resultA).extracting(e -> e.id).contains(entryA1.id);
        assertThat(resultA).extracting(e -> e.id).doesNotContain(entryB1.id);

        // Query as tenant B — should only see B's entry
        principal.setTenancyId(TENANT_B);
        AuditQuery queryB = AuditQuery.builder()
                .actorId("bob")
                .page(0)
                .size(100)
                .build();
        List<AuditEntry> resultB = store.query(queryB);
        assertThat(resultB).extracting(e -> e.id).contains(entryB1.id);
        assertThat(resultB).extracting(e -> e.id).doesNotContain(entryA1.id);
    }

    // -------------------------------------------------------------------------
    // count() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void count_returnsOnlyCurrentTenantEntries() {
        // Create work item for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("tenant-a-workitem");
        workItemStore.put(wiA);

        // Create audit entries for tenant A
        AuditEntry entryA1 = newAuditEntry(wiA.id, "CREATED");
        entryA1.actor = "alice";
        store.append(entryA1);

        AuditEntry entryA2 = newAuditEntry(wiA.id, "ASSIGNED");
        entryA2.actor = "alice";
        store.append(entryA2);

        // Create work item for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("tenant-b-workitem");
        workItemStore.put(wiB);

        // Create audit entry for tenant B
        AuditEntry entryB1 = newAuditEntry(wiB.id, "CREATED");
        entryB1.actor = "bob";
        store.append(entryB1);

        // Count as tenant A — should count 2
        principal.setTenancyId(TENANT_A);
        AuditQuery queryA = AuditQuery.builder()
                .actorId("alice")
                .page(0)
                .size(100)
                .build();
        long countA = store.count(queryA);
        assertThat(countA).isEqualTo(2);

        // Count as tenant B — should count 1
        principal.setTenancyId(TENANT_B);
        AuditQuery queryB = AuditQuery.builder()
                .actorId("bob")
                .page(0)
                .size(100)
                .build();
        long countB = store.count(queryB);
        assertThat(countB).isEqualTo(1);
    }
}

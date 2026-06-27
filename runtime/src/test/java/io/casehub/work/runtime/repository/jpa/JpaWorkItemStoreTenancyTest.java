package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemStore store;

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

    // -------------------------------------------------------------------------
    // put() stamps tenancyId
    // -------------------------------------------------------------------------

    @Test
    void put_stampsTeancyId_whenNull() {
        principal.setTenancyId(TENANT_A);

        WorkItem wi = newWorkItem("stamp-test");
        assertThat(wi.tenancyId).isNull();

        store.put(wi);

        assertThat(wi.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void put_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);

        WorkItem wi = newWorkItem("preserve-test");
        wi.tenancyId = TENANT_A; // explicitly set to A

        store.put(wi);

        // Should keep A, not overwrite with B
        assertThat(wi.tenancyId).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // get() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_forAnotherTenantItem() {
        // Create item as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wi = newWorkItem("get-isolation");
        store.put(wi);
        UUID id = wi.id;

        // Switch to tenant B — should not see A's item
        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        // Switch back to A — should see it
        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    // -------------------------------------------------------------------------
    // findByCallerRef() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void findByCallerRef_returnsEmpty_forAnotherTenantCallerRef() {
        String callerRef = "case:" + UUID.randomUUID() + "/pi:" + UUID.randomUUID();

        // Create item as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wi = newWorkItem("callerref-isolation");
        wi.callerRef = callerRef;
        store.put(wi);

        // Switch to tenant B — should not find by callerRef
        principal.setTenancyId(TENANT_B);
        assertThat(store.findByCallerRef(callerRef)).isEmpty();

        // Switch back to A — should find it
        principal.setTenancyId(TENANT_A);
        assertThat(store.findByCallerRef(callerRef)).isPresent();
    }

    // -------------------------------------------------------------------------
    // scan() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void scan_returnsOnlyCurrentTenantItems() {
        // Create items for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("scan-tenant-a");
        wiA.assigneeId = "alice";
        store.put(wiA);

        // Create items for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("scan-tenant-b");
        wiB.assigneeId = "alice";
        store.put(wiB);

        // Scan as tenant A — should only see A's item
        principal.setTenancyId(TENANT_A);
        List<WorkItem> resultA = store.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(resultA).extracting(w -> w.id).contains(wiA.id);
        assertThat(resultA).extracting(w -> w.id).doesNotContain(wiB.id);

        // Scan as tenant B — should only see B's item
        principal.setTenancyId(TENANT_B);
        List<WorkItem> resultB = store.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(resultB).extracting(w -> w.id).contains(wiB.id);
        assertThat(resultB).extracting(w -> w.id).doesNotContain(wiA.id);
    }

    // -------------------------------------------------------------------------
    // scanAll() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void scanAll_returnsOnlyCurrentTenantItems() {
        // Create items for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = newWorkItem("scanall-tenant-a");
        store.put(wiA);

        // Create items for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = newWorkItem("scanall-tenant-b");
        store.put(wiB);

        // scanAll as tenant A — should only see A's item
        principal.setTenancyId(TENANT_A);
        List<WorkItem> resultA = store.scanAll();
        assertThat(resultA).extracting(w -> w.id).contains(wiA.id);
        assertThat(resultA).extracting(w -> w.id).doesNotContain(wiB.id);

        // scanAll as tenant B — should only see B's item
        principal.setTenancyId(TENANT_B);
        List<WorkItem> resultB = store.scanAll();
        assertThat(resultB).extracting(w -> w.id).contains(wiB.id);
        assertThat(resultB).extracting(w -> w.id).doesNotContain(wiA.id);
    }

    // -------------------------------------------------------------------------
    // countByParentAndAssignee — terminal status exclusion
    // -------------------------------------------------------------------------

    @Test
    void countByParentAndAssignee_excludesAllTerminalStatuses() {
        principal.setTenancyId(TENANT_A);

        WorkItem parent = newWorkItem("Parent");
        store.put(parent);
        final UUID parentId = parent.id;

        // One active child as the baseline count
        WorkItem activeChild = newWorkItem("Active child");
        activeChild.parentId = parentId;
        activeChild.assigneeId = "bob";
        activeChild.status = WorkItemStatus.IN_PROGRESS;
        store.put(activeChild);

        // One child in each terminal status — none should be counted
        List<WorkItemStatus> terminalStatuses = Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isTerminal)
                .toList();
        for (WorkItemStatus status : terminalStatuses) {
            WorkItem terminalChild = newWorkItem("Terminal-" + status);
            terminalChild.parentId = parentId;
            terminalChild.assigneeId = "bob";
            terminalChild.status = status;
            store.put(terminalChild);
        }

        long count = store.countByParentAndAssignee(parentId, "bob", UUID.randomUUID());
        assertThat(count)
                .as("Only non-terminal WorkItems should be counted — all %d terminal statuses must be excluded: %s",
                        terminalStatuses.size(), terminalStatuses)
                .isEqualTo(1L);
    }
}

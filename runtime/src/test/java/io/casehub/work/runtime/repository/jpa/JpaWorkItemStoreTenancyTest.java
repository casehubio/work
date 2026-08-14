package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.repository.WorkItemEntityMapper;
import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.api.spi.WorkItemStore;
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

    private WorkItemEntity newWorkItem(String title) {
        WorkItemEntity wi = new WorkItemEntity();
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

        WorkItemEntity wi = newWorkItem("stamp-test");
        assertThat(wi.tenancyId).isNull();

        WorkItem result = store.put(WorkItemEntityMapper.toDomain(wi));

        assertThat(result.tenancyId()).isEqualTo(TENANT_A);
    }

    @Test
    void put_preservesTenancyId_whenAlreadySet() {
        principal.setTenancyId(TENANT_B);

        WorkItemEntity wi = newWorkItem("preserve-test");
        wi.tenancyId = TENANT_A; // explicitly set to A

        WorkItem result = store.put(WorkItemEntityMapper.toDomain(wi));

        // Should keep A, not overwrite with B
        assertThat(result.tenancyId()).isEqualTo(TENANT_A);
    }

    // -------------------------------------------------------------------------
    // get() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_forAnotherTenantItem() {
        // Create item as tenant A
        principal.setTenancyId(TENANT_A);
        WorkItemEntity wi = newWorkItem("get-isolation");
        WorkItem result = store.put(WorkItemEntityMapper.toDomain(wi));
        UUID id = result.id();

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
        WorkItemEntity wi = newWorkItem("callerref-isolation");
        wi.callerRef = callerRef;
        store.put(WorkItemEntityMapper.toDomain(wi));

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
        WorkItemEntity wiAEntity = newWorkItem("scan-tenant-a");
        wiAEntity.assigneeId = "alice";
        WorkItem wiA = store.put(WorkItemEntityMapper.toDomain(wiAEntity));

        // Create items for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItemEntity wiBEntity = newWorkItem("scan-tenant-b");
        wiBEntity.assigneeId = "alice";
        WorkItem wiB = store.put(WorkItemEntityMapper.toDomain(wiBEntity));

        // Scan as tenant A — should only see A's item
        principal.setTenancyId(TENANT_A);
        List<WorkItem> resultA = store.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(resultA).extracting(w -> w.id()).contains(wiA.id());
        assertThat(resultA).extracting(w -> w.id()).doesNotContain(wiB.id());

        // Scan as tenant B — should only see B's item
        principal.setTenancyId(TENANT_B);
        List<WorkItem> resultB = store.scan(WorkItemQuery.inbox("alice", null, null));
        assertThat(resultB).extracting(w -> w.id()).contains(wiB.id());
        assertThat(resultB).extracting(w -> w.id()).doesNotContain(wiA.id());
    }

    // -------------------------------------------------------------------------
    // scanAll() tenant isolation
    // -------------------------------------------------------------------------

    @Test
    void scanAll_returnsOnlyCurrentTenantItems() {
        // Create items for tenant A
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = store.put(WorkItemEntityMapper.toDomain(newWorkItem("scanall-tenant-a")));

        // Create items for tenant B
        principal.setTenancyId(TENANT_B);
        WorkItem wiB = store.put(WorkItemEntityMapper.toDomain(newWorkItem("scanall-tenant-b")));

        // scanAll as tenant A — should only see A's item
        principal.setTenancyId(TENANT_A);
        List<WorkItem> resultA = store.scanAll();
        assertThat(resultA).extracting(w -> w.id()).contains(wiA.id());
        assertThat(resultA).extracting(w -> w.id()).doesNotContain(wiB.id());

        // scanAll as tenant B — should only see B's item
        principal.setTenancyId(TENANT_B);
        List<WorkItem> resultB = store.scanAll();
        assertThat(resultB).extracting(w -> w.id()).contains(wiB.id());
        assertThat(resultB).extracting(w -> w.id()).doesNotContain(wiA.id());
    }

    // -------------------------------------------------------------------------
    // countByParentAndAssignee — terminal status exclusion
    // -------------------------------------------------------------------------

    @Test
    void countByParentAndAssignee_excludesAllTerminalStatuses() {
        principal.setTenancyId(TENANT_A);

        WorkItem parent = store.put(WorkItemEntityMapper.toDomain(newWorkItem("Parent")));
        final UUID parentId = parent.id();

        // One active child as the baseline count
        WorkItemEntity activeChild = newWorkItem("Active child");
        activeChild.parentId = parentId;
        activeChild.assigneeId = "bob";
        activeChild.status = WorkItemStatus.IN_PROGRESS;
        store.put(WorkItemEntityMapper.toDomain(activeChild));

        // One child in each terminal status — none should be counted
        List<WorkItemStatus> terminalStatuses = Arrays.stream(WorkItemStatus.values())
                .filter(WorkItemStatus::isTerminal)
                .toList();
        for (WorkItemStatus status : terminalStatuses) {
            WorkItemEntity terminalChild = newWorkItem("Terminal-" + status);
            terminalChild.parentId = parentId;
            terminalChild.assigneeId = "bob";
            terminalChild.status = status;
            store.put(WorkItemEntityMapper.toDomain(terminalChild));
        }

        long count = store.countByParentAndAssignee(parentId, "bob", UUID.randomUUID());
        assertThat(count)
                .as("Only non-terminal WorkItems should be counted — all %d terminal statuses must be excluded: %s",
                        terminalStatuses.size(), terminalStatuses)
                .isEqualTo(1L);
    }
}

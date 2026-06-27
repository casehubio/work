package io.casehub.work.queues.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.queues.model.WorkItemQueueState;
import io.casehub.work.queues.repository.QueueStateStore;
import io.casehub.work.queues.test.MutableCurrentPrincipal;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaQueueStateStore}.
 */
@QuarkusTest
@TestTransaction
class JpaQueueStateStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    QueueStateStore store;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a parent WorkItem so FK constraints are satisfied. */
    private UUID createParentWorkItem(String title) {
        WorkItem wi = new WorkItem();
        wi.title = title;
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        workItemStore.put(wi);
        return wi.id;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        UUID workItemId = createParentWorkItem("state-put-test");

        WorkItemQueueState s = new WorkItemQueueState();
        s.workItemId = workItemId;
        assertThat(s.tenancyId).isNull();
        store.put(s);
        assertThat(s.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantItem() {
        principal.setTenancyId(TENANT_A);
        UUID workItemId = createParentWorkItem("state-get-test");

        WorkItemQueueState s = new WorkItemQueueState();
        s.workItemId = workItemId;
        store.put(s);

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(workItemId)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(workItemId)).isPresent();
    }

    @Test
    void findOrCreate_returnsExistingForSameTenant() {
        principal.setTenancyId(TENANT_A);
        UUID workItemId = createParentWorkItem("state-findorcreate-test");

        WorkItemQueueState s = store.findOrCreate(workItemId);
        assertThat(s).isNotNull();
        assertThat(s.tenancyId).isEqualTo(TENANT_A);

        // Find again for same tenant — should return the same entity
        WorkItemQueueState s2 = store.findOrCreate(workItemId);
        assertThat(s2.workItemId).isEqualTo(s.workItemId);
    }

    @Test
    void get_returnsEmpty_forOtherTenantState() {
        // Create work item and state as tenant A
        principal.setTenancyId(TENANT_A);
        UUID workItemId = createParentWorkItem("state-cross-tenant-test");
        store.findOrCreate(workItemId);

        // Tenant B should not see tenant A's state
        principal.setTenancyId(TENANT_B);
        assertThat(store.get(workItemId)).isEmpty();
    }
}

package io.casehub.work.queues.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.queues.model.WorkItemQueueMembership;
import io.casehub.work.queues.repository.QueueMembershipStore;
import io.casehub.work.queues.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaQueueMembershipStore}.
 */
@QuarkusTest
@TestTransaction
class JpaQueueMembershipStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    QueueMembershipStore store;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    private WorkItemQueueMembership newMembership(UUID workItemId) {
        WorkItemQueueMembership m = new WorkItemQueueMembership();
        m.workItemId = workItemId;
        m.queueViewId = UUID.randomUUID();
        m.queueName = "Test Queue";
        return m;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        WorkItemQueueMembership m = newMembership(UUID.randomUUID());
        assertThat(m.tenancyId).isNull();
        store.put(m);
        assertThat(m.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void findByWorkItemId_returnsEmpty_forAnotherTenant() {
        UUID workItemId = UUID.randomUUID();

        principal.setTenancyId(TENANT_A);
        store.put(newMembership(workItemId));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByWorkItemId(workItemId)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByWorkItemId(workItemId)).hasSize(1);
    }

    @Test
    void deleteByWorkItemId_doesNotDeleteAnotherTenantRows() {
        UUID workItemId = UUID.randomUUID();

        principal.setTenancyId(TENANT_A);
        store.put(newMembership(workItemId));

        principal.setTenancyId(TENANT_B);
        store.deleteByWorkItemId(workItemId);

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByWorkItemId(workItemId)).hasSize(1);
    }
}

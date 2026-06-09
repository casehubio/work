package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.model.WorkItemLinkType;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemLinkStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tenant isolation tests for {@link JpaWorkItemLinkStore}.
 *
 * <p>Each test switches between two tenants via {@link MutableCurrentPrincipal} and
 * verifies that queries never leak data across tenant boundaries.
 */
@QuarkusTest
@TestTransaction
class JpaWorkItemLinkStoreTenancyTest {

    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Inject
    WorkItemLinkStore store;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void resetPrincipal() {
        principal.reset();
    }

    /** Create a minimal WorkItem in the current tenant. */
    private WorkItem createWorkItem() {
        WorkItem wi = new WorkItem();
        wi.title = "test-" + UUID.randomUUID();
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.MEDIUM;
        wi.createdBy = "test";
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = Instant.now().plusSeconds(3600);
        return workItemStore.put(wi);
    }

    private WorkItemLink newLink(UUID workItemId, String url, String type) {
        WorkItemLink link = new WorkItemLink();
        link.workItemId = workItemId;
        link.url = url;
        link.relationType = type;
        link.linkedBy = "test";
        link.createdAt = Instant.now();
        return link;
    }

    @Test
    void put_stampsTenancyId_whenNull() {
        principal.setTenancyId(TENANT_A);
        WorkItem wi = createWorkItem();

        WorkItemLink link = newLink(wi.id, "https://example.com", WorkItemLinkType.REFERENCE);
        assertThat(link.tenancyId).isNull();

        store.put(link);

        assertThat(link.tenancyId).isEqualTo(TENANT_A);
    }

    @Test
    void get_returnsEmpty_forAnotherTenantLink() {
        principal.setTenancyId(TENANT_A);
        WorkItem wi = createWorkItem();
        WorkItemLink link = newLink(wi.id, "https://example.com", WorkItemLinkType.REFERENCE);
        store.put(link);
        UUID id = link.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.get(id)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
    }

    @Test
    void findByWorkItemId_returnsOnlyCurrentTenantLinks() {
        // Each tenant creates its own WorkItem and link
        principal.setTenancyId(TENANT_A);
        WorkItem wiA = createWorkItem();
        store.put(newLink(wiA.id, "https://a.example.com", WorkItemLinkType.REFERENCE));

        principal.setTenancyId(TENANT_B);
        WorkItem wiB = createWorkItem();
        store.put(newLink(wiB.id, "https://b.example.com", WorkItemLinkType.ATTACHMENT));

        List<WorkItemLink> resultB = store.findByWorkItemId(wiB.id);
        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).url).isEqualTo("https://b.example.com");

        principal.setTenancyId(TENANT_A);
        List<WorkItemLink> resultA = store.findByWorkItemId(wiA.id);
        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).url).isEqualTo("https://a.example.com");
    }

    @Test
    void findByWorkItemIdAndType_tenantIsolated() {
        principal.setTenancyId(TENANT_A);
        WorkItem wi = createWorkItem();
        store.put(newLink(wi.id, "https://a.example.com", WorkItemLinkType.REFERENCE));

        principal.setTenancyId(TENANT_B);
        assertThat(store.findByWorkItemIdAndType(wi.id, WorkItemLinkType.REFERENCE)).isEmpty();

        principal.setTenancyId(TENANT_A);
        assertThat(store.findByWorkItemIdAndType(wi.id, WorkItemLinkType.REFERENCE)).hasSize(1);
    }

    @Test
    void delete_cannotDeleteAnotherTenantLink() {
        principal.setTenancyId(TENANT_A);
        WorkItem wi = createWorkItem();
        WorkItemLink link = newLink(wi.id, "https://example.com", WorkItemLinkType.REFERENCE);
        store.put(link);
        UUID id = link.id;

        principal.setTenancyId(TENANT_B);
        assertThat(store.delete(id)).isFalse();

        principal.setTenancyId(TENANT_A);
        assertThat(store.get(id)).isPresent();
        assertThat(store.delete(id)).isTrue();
        assertThat(store.get(id)).isEmpty();
    }
}

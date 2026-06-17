package io.casehub.work.mongodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MongoCrossTenantWorkItemStoreTest {

    @Inject
    MutableCurrentPrincipal principal;

    @Inject
    CrossTenantWorkItemStore unqualifiedStore;

    @Inject
    @CrossTenant
    CrossTenantWorkItemStore crossTenantStore;

    @BeforeEach
    void setUp() {
        principal.reset();
        MongoWorkItemDocument.deleteAll();
    }

    @Test
    void findActiveWithDeadlines_returnsItemsAcrossTenants() {
        principal.setTenancyId("tenant-a");
        persistWorkItem("Tenant A expiry", WorkItemStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS), null);

        principal.setTenancyId("tenant-b");
        persistWorkItem("Tenant B claim", WorkItemStatus.ASSIGNED,
                null, Instant.now().plus(2, ChronoUnit.HOURS));

        List<WorkItem> results = unqualifiedStore.findActiveWithDeadlines();

        assertThat(results).hasSize(2)
                .extracting(w -> w.title)
                .containsExactlyInAnyOrder("Tenant A expiry", "Tenant B claim");
    }

    @Test
    void findActiveWithDeadlines_excludesTerminalStatuses() {
        principal.setTenancyId("tenant-a");
        persistWorkItem("Completed", WorkItemStatus.COMPLETED,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        persistWorkItem("Expired", WorkItemStatus.EXPIRED,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        persistWorkItem("Cancelled", WorkItemStatus.CANCELLED,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        persistWorkItem("Rejected", WorkItemStatus.REJECTED,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        persistWorkItem("Escalated", WorkItemStatus.ESCALATED,
                Instant.now().plus(1, ChronoUnit.HOURS), null);
        persistWorkItem("Active", WorkItemStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS), null);

        List<WorkItem> results = unqualifiedStore.findActiveWithDeadlines();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Active");
    }

    @Test
    void findActiveWithDeadlines_excludesItemsWithNoDeadlines() {
        principal.setTenancyId("tenant-a");
        persistWorkItem("No deadline", WorkItemStatus.PENDING, null, null);
        persistWorkItem("Has expiry", WorkItemStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS), null);

        List<WorkItem> results = unqualifiedStore.findActiveWithDeadlines();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Has expiry");
    }

    @Test
    void cdiWiring_crossTenantQualifier_resolvesToMongoViaProducer() {
        principal.setTenancyId("tenant-a");
        persistWorkItem("Wiring test", WorkItemStatus.PENDING,
                Instant.now().plus(1, ChronoUnit.HOURS), null);

        List<WorkItem> results = crossTenantStore.findActiveWithDeadlines();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title).isEqualTo("Wiring test");
    }

    private void persistWorkItem(String title, WorkItemStatus status,
            Instant expiresAt, Instant claimDeadline) {
        WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.tenancyId = principal.tenancyId();
        wi.title = title;
        wi.status = status;
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        wi.createdBy = "test";
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        MongoWorkItemDocument.from(wi).persist();
    }
}

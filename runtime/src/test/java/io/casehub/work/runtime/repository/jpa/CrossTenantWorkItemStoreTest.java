package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests that {@link CrossTenantWorkItemStore} sees items from all tenants,
 * while the tenant-scoped {@link WorkItemStore} respects tenant isolation.
 */
@QuarkusTest
class CrossTenantWorkItemStoreTest {

    @Inject
    WorkItemStore tenantStore;

    @Inject
    @CrossTenant
    CrossTenantWorkItemStore crossTenantStore;

    @Inject
    MutableCurrentPrincipal principal;

    @BeforeEach
    void setUp() {
        principal.reset();
    }

    @Test
    @Transactional
    void findActiveWithDeadlines_returns_items_from_all_tenants() {
        // Create item A in tenant-a with expiresAt
        principal.setTenancyId("tenant-a");
        WorkItem itemA = createItemWithExpiresAt("tenant-a");
        tenantStore.put(itemA);

        // Create item B in tenant-b with claimDeadline
        principal.setTenancyId("tenant-b");
        WorkItem itemB = createItemWithClaimDeadline("tenant-b");
        tenantStore.put(itemB);

        // Cross-tenant store should see both (at minimum)
        var all = crossTenantStore.findActiveWithDeadlines();
        var created = all.stream()
            .filter(wi -> wi.id.equals(itemA.id) || wi.id.equals(itemB.id))
            .toList();
        assertThat(created).hasSize(2);
        assertThat(created.stream().map(wi -> wi.tenancyId).distinct().toList())
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    }

    @Test
    @Transactional
    void findActiveWithDeadlines_excludes_terminal_statuses() {
        principal.setTenancyId("tenant-a");

        // Active item with deadline
        WorkItem active = createItemWithExpiresAt("tenant-a");
        active.status = WorkItemStatus.IN_PROGRESS;
        tenantStore.put(active);

        // Completed item with deadline (should be excluded)
        WorkItem completed = createItemWithExpiresAt("tenant-a");
        completed.status = WorkItemStatus.COMPLETED;
        tenantStore.put(completed);

        // Expired item with deadline (should be excluded)
        WorkItem expired = createItemWithExpiresAt("tenant-a");
        expired.status = WorkItemStatus.EXPIRED;
        tenantStore.put(expired);

        var results = crossTenantStore.findActiveWithDeadlines();
        // Should contain the active item, but not the completed or expired ones
        assertThat(results.stream().anyMatch(wi -> wi.id.equals(active.id))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(completed.id))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(expired.id))).isTrue();
    }

    @Test
    @Transactional
    void findActiveWithDeadlines_excludes_items_without_deadlines() {
        principal.setTenancyId("tenant-a");

        // Item with deadline
        WorkItem withDeadline = createItemWithExpiresAt("tenant-a");
        tenantStore.put(withDeadline);

        // Item without deadline
        WorkItem withoutDeadline = new WorkItem();
        withoutDeadline.id = UUID.randomUUID();
        withoutDeadline.tenancyId = "tenant-a";
        withoutDeadline.title = "No deadline";
        withoutDeadline.status = WorkItemStatus.PENDING;
        withoutDeadline.priority = WorkItemPriority.MEDIUM;
        withoutDeadline.expiresAt = null;
        withoutDeadline.claimDeadline = null;
        tenantStore.put(withoutDeadline);

        var results = crossTenantStore.findActiveWithDeadlines();
        // Should contain the item with deadline
        assertThat(results.stream().anyMatch(wi -> wi.id.equals(withDeadline.id))).isTrue();
        // Should NOT contain the item without deadline
        assertThat(results.stream().noneMatch(wi -> wi.id.equals(withoutDeadline.id))).isTrue();
    }

    @Test
    @Transactional
    void tenantScopedStore_never_returns_cross_tenant_data() {
        // Create items in two tenants
        principal.setTenancyId("tenant-a");
        WorkItem itemA = createItemWithExpiresAt("tenant-a");
        tenantStore.put(itemA);

        principal.setTenancyId("tenant-b");
        WorkItem itemB = createItemWithExpiresAt("tenant-b");
        tenantStore.put(itemB);

        // Even with crossTenantAdmin flag, tenant-scoped store only sees tenant-a
        principal.setCrossTenantAdmin(true);
        principal.setTenancyId("tenant-a");
        var results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId).isEqualTo("tenant-a"));

        // Switch to tenant-b, should only see tenant-b
        principal.setTenancyId("tenant-b");
        results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId).isEqualTo("tenant-b"));
    }

    private WorkItem createItemWithExpiresAt(String tenancyId) {
        WorkItem item = new WorkItem();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with expiresAt";
        item.status = WorkItemStatus.PENDING;
        item.priority = WorkItemPriority.MEDIUM;
        item.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        return item;
    }

    private WorkItem createItemWithClaimDeadline(String tenancyId) {
        WorkItem item = new WorkItem();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with claimDeadline";
        item.status = WorkItemStatus.ASSIGNED;
        item.priority = WorkItemPriority.MEDIUM;
        item.claimDeadline = Instant.now().plus(1, ChronoUnit.HOURS);
        return item;
    }
}

package io.casehub.work.runtime.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import io.casehub.work.api.WorkItem;
import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.repository.WorkItemEntityMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemQuery;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.api.spi.CrossTenantWorkItemStore;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.runtime.test.MutableCurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Tests that {@link CrossTenantWorkItemStore} sees items from all tenants,
 * while the tenant-scoped {@link WorkItemStore} respects tenant isolation.
 *
 * <p>Data is persisted in committed transactions (via {@link #inTx}) because the
 * cross-tenant store uses {@code @Transactional(REQUIRES_NEW)} — it cannot see
 * uncommitted data from the test's transaction.
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
    void findActiveWithDeadlines_returns_items_from_all_tenants() {
        principal.setTenancyId("tenant-a");
        WorkItem itemA = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(createItemWithExpiresAt("tenant-a"))));

        principal.setTenancyId("tenant-b");
        WorkItem itemB = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(createItemWithClaimDeadline("tenant-b"))));

        var all = crossTenantStore.findActiveWithDeadlines();
        var created = all.stream()
            .filter(wi -> wi.id().equals(itemA.id()) || wi.id().equals(itemB.id()))
            .toList();
        assertThat(created).hasSize(2);
        assertThat(created.stream().map(wi -> wi.tenancyId()).distinct().toList())
            .containsExactlyInAnyOrder("tenant-a", "tenant-b");
    }

    @Test
    void findActiveWithDeadlines_excludesAllTerminalStatuses() {
        principal.setTenancyId("tenant-a");

        WorkItemEntity activeEntity = createItemWithExpiresAt("tenant-a");
        activeEntity.status = WorkItemStatus.IN_PROGRESS;
        WorkItem active = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(activeEntity)));

        List<UUID> terminalIds = new ArrayList<>();
        for (WorkItemStatus status : WorkItemStatus.values()) {
            if (status.isTerminal()) {
                WorkItemEntity terminal = createItemWithExpiresAt("tenant-a");
                terminal.status = status;
                WorkItem saved = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(terminal)));
                terminalIds.add(saved.id());
            }
        }

        var results = crossTenantStore.findActiveWithDeadlines();
        assertThat(results.stream().anyMatch(wi -> wi.id().equals(active.id())))
                .as("Active item with deadline should be returned")
                .isTrue();
        for (UUID terminalId : terminalIds) {
            assertThat(results.stream().noneMatch(wi -> wi.id().equals(terminalId)))
                    .as("Terminal item %s should be excluded", terminalId)
                    .isTrue();
        }
    }

    @Test
    void findActiveWithDeadlines_excludes_items_without_deadlines() {
        principal.setTenancyId("tenant-a");

        WorkItemEntity withDeadlineEntity = createItemWithExpiresAt("tenant-a");
        WorkItem withDeadline = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(withDeadlineEntity)));

        WorkItemEntity withoutDeadlineEntity = new WorkItemEntity();
        withoutDeadlineEntity.id = UUID.randomUUID();
        withoutDeadlineEntity.tenancyId = "tenant-a";
        withoutDeadlineEntity.title = "No deadline";
        withoutDeadlineEntity.status = WorkItemStatus.PENDING;
        withoutDeadlineEntity.priority = WorkItemPriority.MEDIUM;
        withoutDeadlineEntity.expiresAt = null;
        withoutDeadlineEntity.claimDeadline = null;
        WorkItem withoutDeadline = inTx(() -> tenantStore.put(WorkItemEntityMapper.toDomain(withoutDeadlineEntity)));

        var results = crossTenantStore.findActiveWithDeadlines();
        assertThat(results.stream().anyMatch(wi -> wi.id().equals(withDeadline.id()))).isTrue();
        assertThat(results.stream().noneMatch(wi -> wi.id().equals(withoutDeadline.id()))).isTrue();
    }

    @Test
    @Transactional
    void tenantScopedStore_never_returns_cross_tenant_data() {
        principal.setTenancyId("tenant-a");
        WorkItemEntity itemA = createItemWithExpiresAt("tenant-a");
        tenantStore.put(WorkItemEntityMapper.toDomain(itemA));

        principal.setTenancyId("tenant-b");
        WorkItemEntity itemB = createItemWithExpiresAt("tenant-b");
        tenantStore.put(WorkItemEntityMapper.toDomain(itemB));

        principal.setCrossTenantAdmin(true);
        principal.setTenancyId("tenant-a");
        var results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId()).isEqualTo("tenant-a"));

        principal.setTenancyId("tenant-b");
        results = tenantStore.scan(WorkItemQuery.all());
        assertThat(results).hasSize(1);
        assertThat(results).allSatisfy(wi -> assertThat(wi.tenancyId()).isEqualTo("tenant-b"));
    }

    @Transactional
    <T> T inTx(Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(Runnable r) {
        r.run();
    }

    private WorkItemEntity createItemWithExpiresAt(String tenancyId) {
        WorkItemEntity item = new WorkItemEntity();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with expiresAt";
        item.status = WorkItemStatus.PENDING;
        item.priority = WorkItemPriority.MEDIUM;
        item.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        return item;
    }

    private WorkItemEntity createItemWithClaimDeadline(String tenancyId) {
        WorkItemEntity item = new WorkItemEntity();
        item.id = UUID.randomUUID();
        item.tenancyId = tenancyId;
        item.title = "Item with claimDeadline";
        item.status = WorkItemStatus.ASSIGNED;
        item.priority = WorkItemPriority.MEDIUM;
        item.claimDeadline = Instant.now().plus(1, ChronoUnit.HOURS);
        return item;
    }
}

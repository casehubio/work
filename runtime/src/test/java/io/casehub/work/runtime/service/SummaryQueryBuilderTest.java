package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.casehub.work.runtime.model.WorkItemEntity;
import io.casehub.work.runtime.model.WorkItemLabelEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SummaryQueryBuilderTest {

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void build_computesCorrectAggregates() {
        final String tenancyId = "summary-builder-test-" + UUID.randomUUID();
        final Instant now = Instant.now();

        createWi(tenancyId, WorkItemStatus.PENDING, WorkItemPriority.HIGH, null, null);
        createWi(tenancyId, WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, now.minusSeconds(3600), null);
        createWi(tenancyId, WorkItemStatus.ASSIGNED, WorkItemPriority.HIGH, null, null);
        createWi(tenancyId, WorkItemStatus.COMPLETED, WorkItemPriority.LOW, now.minusSeconds(100), null);

        final Map<String, Object> params = new HashMap<>();
        params.put("tid", tenancyId);
        final WorkItemSummary summary = SummaryQueryBuilder.build(
                em, "FROM WorkItemEntity wi WHERE wi.tenancyId = :tid", params, false, now);

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.byStatus()).containsEntry("PENDING", 2L);
        assertThat(summary.byStatus()).containsEntry("ASSIGNED", 1L);
        assertThat(summary.byStatus()).containsEntry("COMPLETED", 1L);
        assertThat(summary.byPriority()).containsEntry("HIGH", 2L);
        assertThat(summary.byPriority()).containsEntry("MEDIUM", 1L);
        assertThat(summary.byPriority()).containsEntry("LOW", 1L);
        assertThat(summary.overdue()).isEqualTo(1);
        assertThat(summary.claimDeadlineBreached()).isEqualTo(0);
        assertThat(summary.oldestCreatedAt()).isNotNull();
    }

    @Test
    @Transactional
    void build_emptyResult_returnsZeroCounts() {
        final Map<String, Object> params = new HashMap<>();
        params.put("tid", "nonexistent-tenant-" + UUID.randomUUID());
        final WorkItemSummary summary = SummaryQueryBuilder.build(
                em, "FROM WorkItemEntity wi WHERE wi.tenancyId = :tid", params, false, Instant.now());

        assertThat(summary.total()).isEqualTo(0);
        assertThat(summary.byStatus()).isEmpty();
        assertThat(summary.byPriority()).isEmpty();
        assertThat(summary.overdue()).isEqualTo(0);
        assertThat(summary.claimDeadlineBreached()).isEqualTo(0);
        assertThat(summary.oldestCreatedAt()).isNull();
    }

    @Test
    @Transactional
    void build_claimDeadlineBreached_countsPendingOnly() {
        final String tenancyId = "claim-breach-test-" + UUID.randomUUID();
        final Instant now = Instant.now();

        createWi(tenancyId, WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, now.minusSeconds(3600));
        createWi(tenancyId, WorkItemStatus.ASSIGNED, WorkItemPriority.MEDIUM, null, now.minusSeconds(3600));

        final Map<String, Object> params = new HashMap<>();
        params.put("tid", tenancyId);
        final WorkItemSummary summary = SummaryQueryBuilder.build(
                em, "FROM WorkItemEntity wi WHERE wi.tenancyId = :tid", params, false, now);

        assertThat(summary.claimDeadlineBreached()).isEqualTo(1);
    }

    @Test
    @Transactional
    void build_oldestCreatedAt_excludesTerminal() {
        final String tenancyId = "oldest-test-" + UUID.randomUUID();
        final Instant old = Instant.parse("2025-01-01T00:00:00Z");
        final Instant recent = Instant.parse("2026-06-01T00:00:00Z");

        final WorkItemEntity completed = createWi(tenancyId, WorkItemStatus.COMPLETED, WorkItemPriority.MEDIUM, null, null);
        completed.createdAt = old;

        final WorkItemEntity pending = createWi(tenancyId, WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null);
        pending.createdAt = recent;

        em.flush();

        final Map<String, Object> params = new HashMap<>();
        params.put("tid", tenancyId);
        final WorkItemSummary summary = SummaryQueryBuilder.build(
                em, "FROM WorkItemEntity wi WHERE wi.tenancyId = :tid", params, false, Instant.now());

        assertThat(summary.oldestCreatedAt()).isEqualTo(recent);
    }

    @Test
    @Transactional
    void build_withDistinct_deduplicatesJoinResults() {
        final String tenancyId = "distinct-test-" + UUID.randomUUID();

        final WorkItemEntity wi = createWi(tenancyId, WorkItemStatus.PENDING, WorkItemPriority.HIGH, null, null);
        wi.labels.add(new WorkItemLabelEntity("test/a", io.casehub.work.api.LabelPersistence.MANUAL, null));
        wi.labels.add(new WorkItemLabelEntity("test/b", io.casehub.work.api.LabelPersistence.MANUAL, null));
        em.flush();

        final Map<String, Object> params = new HashMap<>();
        params.put("tid", tenancyId);

        final WorkItemSummary withDistinct = SummaryQueryBuilder.build(
                em, "FROM WorkItemEntity wi JOIN wi.labels l WHERE wi.tenancyId = :tid",
                params, true, Instant.now());

        assertThat(withDistinct.total()).isEqualTo(1);
        assertThat(withDistinct.byStatus()).containsEntry("PENDING", 1L);
    }

    private WorkItemEntity createWi(String tenancyId, WorkItemStatus status,
                                    WorkItemPriority priority, Instant expiresAt, Instant claimDeadline) {
        final WorkItemEntity wi = new WorkItemEntity();
        wi.id = UUID.randomUUID();
        wi.tenancyId = tenancyId;
        wi.status = status;
        wi.priority = priority;
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        wi.title = "test";
        wi.createdAt = Instant.now();
        wi.updatedAt = wi.createdAt;
        em.persist(wi);
        return wi;
    }
}

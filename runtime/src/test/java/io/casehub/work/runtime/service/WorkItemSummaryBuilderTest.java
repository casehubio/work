package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;

/**
 * Pure unit tests for inbox summary aggregation logic — no Quarkus, no DB.
 */
class WorkItemSummaryBuilderTest {

    // ── byStatus counts ───────────────────────────────────────────────────────

    @Test
    void byStatus_countsCorrectly() {
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null),
                wi(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null, null),
                wi(WorkItemStatus.ASSIGNED, WorkItemPriority.MEDIUM, null, null),
                wi(WorkItemStatus.IN_PROGRESS, WorkItemPriority.URGENT, null, null));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.total()).isEqualTo(4);
        assertThat(summary.byStatus()).containsEntry("PENDING", 2L);
        assertThat(summary.byStatus()).containsEntry("ASSIGNED", 1L);
        assertThat(summary.byStatus()).containsEntry("IN_PROGRESS", 1L);
    }

    // ── byPriority counts ─────────────────────────────────────────────────────

    @Test
    void byPriority_countsCorrectly() {
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.URGENT, null, null),
                wi(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null, null),
                wi(WorkItemStatus.PENDING, WorkItemPriority.HIGH, null, null),
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.byPriority()).containsEntry("URGENT", 1L);
        assertThat(summary.byPriority()).containsEntry("HIGH", 2L);
        assertThat(summary.byPriority()).containsEntry("MEDIUM", 1L);
    }

    // ── overdue count ─────────────────────────────────────────────────────────

    @Test
    void overdue_countsItemsPastExpiresAt() {
        final Instant now = Instant.now();
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, now.minusSeconds(3600), null), // overdue
                wi(WorkItemStatus.IN_PROGRESS, WorkItemPriority.MEDIUM, now.minusSeconds(1), null), // overdue
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, now.plusSeconds(3600), null), // not overdue
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null)); // no deadline

        final var summary = WorkItemSummaryBuilder.build(items, now);

        assertThat(summary.overdue()).isEqualTo(2);
    }

    @Test
    void overdue_excludesTerminalStatuses() {
        final Instant now = Instant.now();
        final var items = List.of(
                wi(WorkItemStatus.COMPLETED, WorkItemPriority.MEDIUM, now.minusSeconds(3600), null),
                wi(WorkItemStatus.REJECTED, WorkItemPriority.MEDIUM, now.minusSeconds(3600), null));

        final var summary = WorkItemSummaryBuilder.build(items, now);

        assertThat(summary.overdue()).isEqualTo(0);
    }

    // ── claimDeadlineBreached count ───────────────────────────────────────────

    @Test
    void claimDeadlineBreached_countsPendingItemsPastClaimDeadline() {
        final Instant now = Instant.now();
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, now.minusSeconds(3600)), // breached
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, now.plusSeconds(3600)), // not breached
                wi(WorkItemStatus.ASSIGNED, WorkItemPriority.MEDIUM, null, now.minusSeconds(3600)), // ASSIGNED — doesn't count
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null)); // no deadline

        final var summary = WorkItemSummaryBuilder.build(items, now);

        assertThat(summary.claimDeadlineBreached()).isEqualTo(1);
    }

    // ── empty list ────────────────────────────────────────────────────────────

    @Test
    void emptyList_returnsZeroCounts() {
        final var summary = WorkItemSummaryBuilder.build(List.of(), Instant.now());

        assertThat(summary.total()).isEqualTo(0);
        assertThat(summary.byStatus()).isEmpty();
        assertThat(summary.byPriority()).isEmpty();
        assertThat(summary.overdue()).isEqualTo(0);
        assertThat(summary.claimDeadlineBreached()).isEqualTo(0);
    }

    // ── null priority handled ─────────────────────────────────────────────────

    @Test
    void nullPriority_notCountedInByPriority() {
        final var items = List.of(
                wi(WorkItemStatus.PENDING, null, null, null));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.byPriority()).isEmpty();
    }

    // ── oldestCreatedAt ──────────────────────────────────────────────────────

    @Test
    void oldestCreatedAt_selectsOldestNonTerminalItem() {
        final Instant oldest = Instant.parse("2026-01-01T00:00:00Z");
        final Instant middle = Instant.parse("2026-06-01T00:00:00Z");
        final Instant newest = Instant.parse("2026-07-01T00:00:00Z");
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null, newest),
                wi(WorkItemStatus.IN_PROGRESS, WorkItemPriority.HIGH, null, null, oldest),
                wi(WorkItemStatus.ASSIGNED, WorkItemPriority.LOW, null, null, middle));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.oldestCreatedAt()).isEqualTo(oldest);
    }

    @Test
    void oldestCreatedAt_excludesTerminalItems() {
        final Instant terminalOldest = Instant.parse("2025-01-01T00:00:00Z");
        final Instant activeOldest = Instant.parse("2026-06-01T00:00:00Z");
        final var items = List.of(
                wi(WorkItemStatus.COMPLETED, WorkItemPriority.MEDIUM, null, null, terminalOldest),
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null, activeOldest));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.oldestCreatedAt()).isEqualTo(activeOldest);
    }

    @Test
    void oldestCreatedAt_nullWhenAllTerminal() {
        final var items = List.of(
                wi(WorkItemStatus.COMPLETED, WorkItemPriority.MEDIUM, null, null, Instant.parse("2026-01-01T00:00:00Z")),
                wi(WorkItemStatus.REJECTED, WorkItemPriority.HIGH, null, null, Instant.parse("2026-02-01T00:00:00Z")));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.oldestCreatedAt()).isNull();
    }

    @Test
    void oldestCreatedAt_nullWhenEmptyList() {
        final var summary = WorkItemSummaryBuilder.build(List.of(), Instant.now());

        assertThat(summary.oldestCreatedAt()).isNull();
    }

    @Test
    void oldestCreatedAt_excludesNullCreatedAt() {
        final Instant known = Instant.parse("2026-06-01T00:00:00Z");
        final var items = List.of(
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null, null),
                wi(WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null, known));

        final var summary = WorkItemSummaryBuilder.build(items, Instant.now());

        assertThat(summary.oldestCreatedAt()).isEqualTo(known);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItem wi(final WorkItemStatus status, final WorkItemPriority priority,
            final Instant expiresAt, final Instant claimDeadline) {
        return wi(status, priority, expiresAt, claimDeadline, null);
    }

    private WorkItem wi(final WorkItemStatus status, final WorkItemPriority priority,
            final Instant expiresAt, final Instant claimDeadline, final Instant createdAt) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = status;
        wi.priority = priority;
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        wi.createdAt = createdAt;
        return wi;
    }
}

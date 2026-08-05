package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.WorkItemPriority;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SummaryParityTest {

    @Inject
    WorkItemStore workItemStore;

    @Test
    @Transactional
    void sqlPath_matchesEntityLoadingPath() {
        final String assignee = "parity-" + UUID.randomUUID();
        final Instant now = Instant.now();

        createWi(assignee, WorkItemStatus.PENDING, WorkItemPriority.HIGH, now.minusSeconds(100), now.minusSeconds(50));
        createWi(assignee, WorkItemStatus.PENDING, WorkItemPriority.MEDIUM, null, null);
        createWi(assignee, WorkItemStatus.ASSIGNED, WorkItemPriority.HIGH, null, null);
        createWi(assignee, WorkItemStatus.IN_PROGRESS, WorkItemPriority.LOW, now.minusSeconds(200), null);
        createWi(assignee, WorkItemStatus.COMPLETED, WorkItemPriority.LOW, now.minusSeconds(500), null);
        createWi(assignee, WorkItemStatus.REJECTED, WorkItemPriority.URGENT, null, null);

        final WorkItemQuery query = WorkItemQuery.inbox(assignee, List.of(), null);

        final WorkItemSummary entityPath = WorkItemSummaryBuilder.build(
                workItemStore.scan(query), now);

        final WorkItemSummary sqlPath = workItemStore.summaryByQuery(query, now);

        assertThat(sqlPath.total()).isEqualTo(entityPath.total());
        assertThat(sqlPath.byStatus()).isEqualTo(entityPath.byStatus());
        assertThat(sqlPath.byPriority()).isEqualTo(entityPath.byPriority());
        assertThat(sqlPath.overdue()).isEqualTo(entityPath.overdue());
        assertThat(sqlPath.claimDeadlineBreached()).isEqualTo(entityPath.claimDeadlineBreached());
        assertThat(sqlPath.oldestCreatedAt())
                .isEqualTo(entityPath.oldestCreatedAt().truncatedTo(ChronoUnit.MICROS));
    }

    private WorkItem createWi(String assignee, WorkItemStatus status,
            WorkItemPriority priority, Instant expiresAt, Instant claimDeadline) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.status = status;
        wi.priority = priority;
        wi.expiresAt = expiresAt;
        wi.claimDeadline = claimDeadline;
        wi.assigneeId = assignee;
        wi.title = "parity test";
        return workItemStore.put(wi);
    }
}

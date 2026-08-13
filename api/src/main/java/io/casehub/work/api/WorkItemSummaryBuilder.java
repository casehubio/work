package io.casehub.work.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class WorkItemSummaryBuilder {

    private WorkItemSummaryBuilder() {
    }

    public static WorkItemSummary build(final List<WorkItem> items, final Instant now) {
        final long total = items.size();

        final Map<String, Long> byStatus = items.stream()
                .filter(wi -> wi.status() != null)
                .collect(Collectors.groupingBy(wi -> wi.status().name(), Collectors.counting()));

        final Map<String, Long> byPriority = items.stream()
                .filter(wi -> wi.priority() != null)
                .collect(Collectors.groupingBy(wi -> wi.priority().name(), Collectors.counting()));

        final long overdue = items.stream()
                .filter(wi -> wi.status() != null && !wi.status().isTerminal())
                .filter(wi -> wi.expiresAt() != null && wi.expiresAt().isBefore(now))
                .count();

        final long claimDeadlineBreached = items.stream()
                .filter(wi -> wi.status() == WorkItemStatus.PENDING)
                .filter(wi -> wi.claimDeadline() != null && wi.claimDeadline().isBefore(now))
                .count();

        final Instant oldestCreatedAt = items.stream()
                .filter(wi -> wi.status() != null && !wi.status().isTerminal())
                .filter(wi -> wi.createdAt() != null)
                .map(wi -> wi.createdAt())
                .min(Instant::compareTo)
                .orElse(null);

        return new WorkItemSummary(total, byStatus, byPriority, overdue, claimDeadlineBreached, oldestCreatedAt);
    }
}

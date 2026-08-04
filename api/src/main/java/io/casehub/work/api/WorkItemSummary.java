package io.casehub.work.api;

import java.time.Instant;
import java.util.Map;

public record WorkItemSummary(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byPriority,
        long overdue,
        long claimDeadlineBreached,
        Instant oldestCreatedAt) {
}

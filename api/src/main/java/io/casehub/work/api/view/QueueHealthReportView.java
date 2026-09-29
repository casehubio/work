package io.casehub.work.api.view;

import java.time.Instant;

public record QueueHealthReportView(Instant timestamp, long overdueCount, long pendingCount,
                                     long avgPendingAgeSeconds, Instant oldestUnclaimedCreatedAt,
                                     long criticalOverdueCount) {
}

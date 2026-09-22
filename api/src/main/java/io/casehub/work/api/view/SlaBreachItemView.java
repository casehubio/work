package io.casehub.work.api.view;

import java.time.Instant;

public record SlaBreachItemView(String workItemId, String types, String priority,
                                 Instant expiresAt, Instant completedAt,
                                 String status, long breachDurationMinutes) {
}

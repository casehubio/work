package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record ScheduleView(
        UUID id,
        String name,
        UUID templateId,
        String cronExpression,
        boolean active,
        String createdBy,
        Instant createdAt,
        Instant lastFiredAt,
        Instant nextFireAt) {
}

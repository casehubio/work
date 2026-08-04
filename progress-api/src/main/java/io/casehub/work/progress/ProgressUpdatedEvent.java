package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProgressUpdatedEvent(
        UUID id,
        UUID progressId,
        String tenancyId,
        String scopeType,
        String scopeId,
        UUID parentProgressId,
        UUID rootProgressId,
        String shapeType,
        JsonNode previousState,
        JsonNode currentState,
        ProgressStatus status,
        ProgressChangeType changeType,
        Instant timestamp
) {}

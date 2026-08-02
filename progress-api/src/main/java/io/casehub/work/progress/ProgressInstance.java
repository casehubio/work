package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProgressInstance(
        UUID id,
        String tenancyId,
        String scopeType,
        String scopeId,
        UUID parentProgressId,
        UUID rootProgressId,
        String shapeType,
        JsonNode definition,
        JsonNode state,
        ProgressStatus status,
        String rollupStrategyId,
        Instant createdAt,
        Instant updatedAt
) {}

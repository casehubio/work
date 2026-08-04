package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ProgressCreateRequest(
        String tenancyId,
        String scopeType,
        String scopeId,
        String shapeType,
        JsonNode state,
        UUID parentProgressId,
        String rollupStrategyId,
        JsonNode definition,
        String rollbackPolicy,
        String visualisationMode
) {
    public ProgressCreateRequest {
        if (tenancyId == null || tenancyId.isBlank()) {
            throw new IllegalArgumentException("tenancyId is required");
        }
        if (scopeType == null || scopeType.isBlank()) {
            throw new IllegalArgumentException("scopeType is required");
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId is required");
        }
        if (shapeType == null || shapeType.isBlank()) {
            throw new IllegalArgumentException("shapeType is required");
        }
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
    }
}

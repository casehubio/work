package io.casehub.work.progress.rest;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record CreateProgressRequest(
        String tenancyId,
        String scopeType,
        String scopeId,
        String shapeType,
        JsonNode state,
        UUID parentProgressId,
        String rollupStrategyId,
        JsonNode definition
) {}

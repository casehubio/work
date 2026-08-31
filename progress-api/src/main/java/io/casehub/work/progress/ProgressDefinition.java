package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

public record ProgressDefinition(
        String name,
        String shapeType,
        JsonNode definition,
        String rollbackPolicy,
        String visualisationMode) {

    public ProgressDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ProgressDefinition name is required");
        }
        if (shapeType == null || shapeType.isBlank()) {
            throw new IllegalArgumentException("ProgressDefinition shapeType is required");
        }
    }
}

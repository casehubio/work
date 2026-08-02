package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;

public interface ShapeValidator {
    void validate(JsonNode state, JsonNode definition);

    String shapeType();
}

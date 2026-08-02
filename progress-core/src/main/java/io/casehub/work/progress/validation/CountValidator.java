package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;

public class CountValidator implements ShapeValidator {

    @Override
    public String shapeType() {
        return "count";
    }

    @Override
    public void validate(JsonNode state, JsonNode definition) {
        JsonNode currentNode = state.get("current");
        JsonNode totalNode = state.get("total");
        if (currentNode == null || !currentNode.isInt()) {
            throw new IllegalArgumentException("Count state requires integer 'current' field");
        }
        if (totalNode == null || !totalNode.isInt()) {
            throw new IllegalArgumentException("Count state requires integer 'total' field");
        }
        int current = currentNode.intValue();
        int total = totalNode.intValue();
        if (current < 0) {
            throw new IllegalArgumentException("Count 'current' must be non-negative, got: " + current);
        }
        if (total < 0) {
            throw new IllegalArgumentException("Count 'total' must be non-negative, got: " + total);
        }
        if (current > total) {
            throw new IllegalArgumentException("Count 'current' (" + current + ") must not exceed 'total' (" + total + ")");
        }
    }
}

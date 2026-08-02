package io.casehub.work.progress.validation;

import com.fasterxml.jackson.databind.JsonNode;

public class PercentageValidator implements ShapeValidator {

    @Override
    public String shapeType() {
        return "percentage";
    }

    @Override
    public void validate(JsonNode state, JsonNode definition) {
        JsonNode valueNode = state.get("value");
        if (valueNode == null || valueNode.isMissingNode()) {
            throw new IllegalArgumentException("Percentage state requires 'value' field");
        }
        if (!valueNode.isInt()) {
            throw new IllegalArgumentException("Percentage 'value' must be an integer, got: " + valueNode.getNodeType());
        }
        int value = valueNode.intValue();
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Percentage 'value' must be in [0, 100], got: " + value);
        }
    }
}

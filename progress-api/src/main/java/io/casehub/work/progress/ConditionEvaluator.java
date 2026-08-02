package io.casehub.work.progress;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean evaluate(String expression, JsonNode context);
}

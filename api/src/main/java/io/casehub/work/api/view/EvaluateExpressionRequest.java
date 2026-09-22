package io.casehub.work.api.view;

import java.util.Map;

public record EvaluateExpressionRequest(
        String conditionLanguage,
        String conditionExpression,
        Map<String, Object> context) {
}

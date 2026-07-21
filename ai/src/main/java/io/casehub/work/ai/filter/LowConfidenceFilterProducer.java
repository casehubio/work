package io.casehub.work.ai.filter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.ai.config.WorkItemsAiConfig;

@ApplicationScoped
public class LowConfidenceFilterProducer {

    @Inject
    WorkItemsAiConfig config;

    @Inject
    ExpressionEngineRegistry expressionRegistry;

    @Produces
    @SuppressWarnings("unchecked")
    public LabelRule lowConfidenceFilter() {
        final double threshold = config.confidenceThreshold();
        var condition = expressionRegistry.compile(
                "jexl",
                "confidenceScore != null && confidenceScore < threshold",
                (Class<Map<String, Object>>) (Class<?>) Map.class,
                Boolean.class,
                Map.of("threshold", threshold));
        return new LabelRule(
                "ai/low-confidence",
                condition,
                List.of(new LabelAction.Add("ai/low-confidence")),
                Set.of("ADD"));
    }
}

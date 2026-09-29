package io.casehub.work.api.view;

import java.time.Instant;
import java.util.UUID;

public record LabelRuleView(
        UUID id,
        String name,
        String description,
        boolean enabled,
        String conditionLanguage,
        String conditionExpression,
        String actionsJson,
        String triggerEvents,
        String scope,
        String source,
        Instant createdAt) {
}

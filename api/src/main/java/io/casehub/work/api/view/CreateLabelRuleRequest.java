package io.casehub.work.api.view;

import java.util.List;

public record CreateLabelRuleRequest(
        String name,
        String description,
        String conditionLanguage,
        String conditionExpression,
        List<LabelActionDto> actions,
        String triggerEvents,
        String scope) {
}

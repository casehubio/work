package io.casehub.work.rest;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;

@ApplicationScoped
class TestFilterProducer {

    @Produces
    LabelRule applyLabelFilter() {
        return new LabelRule("test/apply-label",
                             new LambdaExpression<>(ctx -> {
                                 Object score = ctx.get("confidenceScore");
                                 return score instanceof Number n && n.doubleValue() < 0.5;
                             }),
                             List.of(new LabelAction.Add("ai/test-label")),
                             Set.of("ADD"));
    }
}

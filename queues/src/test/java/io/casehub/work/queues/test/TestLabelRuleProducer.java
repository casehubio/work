package io.casehub.work.queues.test;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.WorkItemPriority;

@ApplicationScoped
public class TestLabelRuleProducer {

    @Produces
    LabelRule highPriorityRule() {
        return new LabelRule("test/high-priority",
                new LambdaExpression<>(ctx -> {
                    Object p = ctx.get("priority");
                    return p instanceof WorkItemPriority wp && wp == WorkItemPriority.HIGH;
                }),
                List.of(new LabelAction.Add("priority/high")));
    }

    @Produces
    LabelRule pendingStatusRule() {
        return new LabelRule("test/pending-status",
                new LambdaExpression<>(ctx -> {
                    Object s = ctx.get("status");
                    return s != null && s.toString().contains("PENDING");
                }),
                List.of(new LabelAction.Add("intake/pending")));
    }
}

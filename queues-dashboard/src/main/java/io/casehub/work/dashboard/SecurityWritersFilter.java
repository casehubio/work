package io.casehub.work.dashboard;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.casehub.platform.api.expression.LambdaExpression;
import io.casehub.platform.api.label.LabelAction;
import io.casehub.platform.api.label.LabelRule;
import io.casehub.work.api.WorkItemStatus;

@ApplicationScoped
public class SecurityWritersFilter {

    @Produces
    public LabelRule securityWritersRule() {
        return new LabelRule(
                "security-writers",
                new LambdaExpression<>(ctx -> {
                    Object cg     = ctx.get("candidateGroups");
                    Object status = ctx.get("status");
                    return cg != null && cg.toString().contains("security-writers")
                           && status instanceof WorkItemStatus ws && !ws.isTerminal();
                }),
                List.of(new LabelAction.Add("review/urgent")));
    }
}

package io.casehub.work.queues.service;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.runtime.model.WorkItem;

@ApplicationScoped
public class QueueMembershipService {

    private final SubjectViewQuery<WorkItem> viewQuery;
    private final ExpressionEngineRegistry   expressionRegistry;

    @Inject
    public QueueMembershipService(
            final SubjectViewQuery<WorkItem> viewQuery,
            final ExpressionEngineRegistry expressionRegistry) {
        this.viewQuery          = viewQuery;
        this.expressionRegistry = expressionRegistry;
    }

    @SuppressWarnings("unchecked")
    public List<WorkItem> evaluateMembers(final SubjectViewSpec queue) {
        var candidates = viewQuery.findByView(queue);
        if (queue.additionalConditions() != null
            && !queue.additionalConditions().isBlank()) {
            var compiled = expressionRegistry.compile("jexl",
                                                      queue.additionalConditions(),
                                                      (Class<Map<String, Object>>) (Class<?>) Map.class,
                                                      Boolean.class);
            candidates = candidates.stream()
                                   .filter(wi -> Boolean.TRUE.equals(compiled.eval(WorkItemContextBuilder.toMap(wi))))
                                   .toList();
        }
        return candidates;
    }

    public int countMembers(final SubjectViewSpec queue) {
        if (queue.additionalConditions() == null
            || queue.additionalConditions().isBlank()) {
            return (int) viewQuery.countByView(queue);
        }
        return evaluateMembers(queue).size();
    }
}

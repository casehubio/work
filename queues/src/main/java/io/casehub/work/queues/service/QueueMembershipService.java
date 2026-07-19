package io.casehub.work.queues.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.work.runtime.model.WorkItem;

@ApplicationScoped
public class QueueMembershipService {

    private final SubjectViewQuery<WorkItem> viewQuery;
    private final FilterEvaluatorRegistry    evaluatorRegistry;

    @Inject
    public QueueMembershipService(
            final SubjectViewQuery<WorkItem> viewQuery,
            final FilterEvaluatorRegistry evaluatorRegistry) {
        this.viewQuery         = viewQuery;
        this.evaluatorRegistry = evaluatorRegistry;
    }

    public List<WorkItem> evaluateMembers(final SubjectViewSpec queue) {
        var candidates = viewQuery.findByView(queue);
        if (queue.additionalConditions() != null
            && !queue.additionalConditions().isBlank()) {
            final var jexl = evaluatorRegistry.find("jexl");
            if (jexl != null) {
                candidates = candidates.stream()
                                       .filter(wi -> jexl.evaluate(wi,
                                                                   ExpressionDescriptor.of("jexl",
                                                                                           queue.additionalConditions())))
                                       .toList();
            }
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

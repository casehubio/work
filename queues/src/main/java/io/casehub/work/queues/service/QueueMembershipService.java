package io.casehub.work.queues.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.queues.model.QueueView;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

@ApplicationScoped
public class QueueMembershipService {

    private final WorkItemStore workItemStore;
    private final FilterEvaluatorRegistry evaluatorRegistry;

    @Inject
    public QueueMembershipService(
            final WorkItemStore workItemStore,
            final FilterEvaluatorRegistry evaluatorRegistry) {
        this.workItemStore = workItemStore;
        this.evaluatorRegistry = evaluatorRegistry;
    }

    public List<WorkItem> evaluateMembers(final QueueView queue) {
        var candidates = workItemStore.scan(
                WorkItemQuery.byLabelPattern(queue.labelPattern));
        if (queue.additionalConditions != null
                && !queue.additionalConditions.isBlank()) {
            final var jexl = evaluatorRegistry.find("jexl");
            if (jexl != null) {
                candidates = candidates.stream()
                        .filter(wi -> jexl.evaluate(wi,
                                ExpressionDescriptor.of("jexl",
                                        queue.additionalConditions)))
                        .toList();
            }
        }
        return candidates;
    }

    public int countMembers(final QueueView queue) {
        if (queue.additionalConditions == null
                || queue.additionalConditions.isBlank()) {
            return (int) workItemStore.countByQuery(
                    WorkItemQuery.byLabelPattern(queue.labelPattern));
        }
        return evaluateMembers(queue).size();
    }
}

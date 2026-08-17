package io.casehub.work.queues.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.casehub.work.api.WorkItem;
<<<<<<< HEAD
=======
import io.casehub.work.api.WorkItemLifecycleEvent;
>>>>>>> 10ac6d40 (feat(#405): dual-mode work — GraphQL, MCP, callback adapters)
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.view.SubjectViewSpec;
import io.casehub.work.api.WorkItemSummary;
import io.casehub.work.queues.repository.WorkItemViewQuery;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.api.WorkItemSummaryBuilder;

@ApplicationScoped
public class QueueMembershipService {

    private final WorkItemViewQuery         viewQuery;
    private final ExpressionEngineRegistry  expressionRegistry;

    @Inject
    public QueueMembershipService(
            final WorkItemViewQuery viewQuery,
            final ExpressionEngineRegistry expressionRegistry) {
        this.viewQuery          = viewQuery;
        this.expressionRegistry = expressionRegistry;
    }

    @io.quarkus.cache.CacheResult(cacheName = "queue-summary", keyGenerator = QueueSummaryCacheKeyGenerator.class)
    public WorkItemSummary summarize(final SubjectViewSpec queue, final Instant now) {
        if (queue.additionalConditions() != null
            && !queue.additionalConditions().isBlank()) {
            return WorkItemSummaryBuilder.build(evaluateMembers(queue), now);
        }
        return viewQuery.summarizeByView(queue, now);
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
                                   .filter(wi -> Boolean.TRUE.equals(compiled.eval(WorkItemContextBuilder.toMap(io.casehub.work.runtime.repository.WorkItemEntityMapper.toDomain(wi)))))
                                   .toList();
        }
        return candidates.stream().map(io.casehub.work.runtime.repository.WorkItemEntityMapper::toDomain).toList();
    }

    @io.quarkus.cache.CacheInvalidateAll(cacheName = "queue-summary")
    void onWorkItemLifecycle(
            @jakarta.enterprise.event.Observes(during = jakarta.enterprise.event.TransactionPhase.AFTER_SUCCESS)
            WorkItemLifecycleEvent event) {
    }

    public int countMembers(final SubjectViewSpec queue) {
        if (queue.additionalConditions() == null
            || queue.additionalConditions().isBlank()) {
            return (int) viewQuery.countByView(queue);
        }
        return evaluateMembers(queue).size();
    }
}

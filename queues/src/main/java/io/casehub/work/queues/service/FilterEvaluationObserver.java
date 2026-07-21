package io.casehub.work.queues.service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import io.casehub.platform.view.SubjectViewOrchestrator;
import io.casehub.work.api.WorkEventType;
import io.casehub.work.queues.event.QueueEventType;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.runtime.event.WorkItemContextBuilder;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.filter.LabelRuleEngine;
import io.casehub.work.runtime.repository.WorkItemStore;

@ApplicationScoped
public class FilterEvaluationObserver {

    @Inject
    LabelRuleEngine labelRuleEngine;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    SubjectViewOrchestrator views;

    @Inject
    Event<WorkItemQueueEvent> queueEventBus;

    @Transactional
    public void onLifecycleEvent(@Observes final WorkItemLifecycleEvent event) {
        workItemStore.get(event.workItemId()).ifPresent(wi -> {
            final String              eventType = mapEventType(event.eventType());
            final Map<String, Object> context   = WorkItemContextBuilder.toMap(wi);

            labelRuleEngine.evaluate(wi, context, eventType);

            final Set<String> labelPaths = wi.labels == null ? Set.of()
                                                             : wi.labels.stream().map(l -> l.path).collect(Collectors.toSet());

            views.evaluateAndTrack(wi.id, wi.tenancyId, labelPaths)
                 .forEach(ve -> queueEventBus.fire(
                         new WorkItemQueueEvent(ve.subjectId(), ve.viewId(),
                                                ve.viewName(),
                                                QueueEventType.valueOf(ve.type().name()),
                                                ve.tenancyId())));
        });
    }

    private String mapEventType(final WorkEventType eventType) {
        return switch (eventType) {
            case CREATED -> "ADD";
            case COMPLETED, REJECTED, FAULTED, CANCELLED, OBSOLETE, EXPIRED, ESCALATED -> "REMOVE";
            default -> "UPDATE";
        };
    }
}

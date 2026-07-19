package io.casehub.work.queues.service;

import io.casehub.platform.api.view.SubjectViewQuery;
import io.casehub.platform.api.view.SubjectViewStore;
import io.casehub.work.queues.event.WorkItemQueueEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class WorkItemQueueMetrics {

    @Inject
    MeterRegistry registry;

    @Inject
    SubjectViewQuery<WorkItem> viewQuery;

    @Inject
    SubjectViewStore viewStore;

    public void onQueueEvent(@Observes final WorkItemQueueEvent event) {
        final var spec = viewStore.findById(event.queueViewId()).orElse(null);
        if (spec == null) {return;}

        Gauge.builder("workitems.queue.depth", viewQuery,
                      q -> q.countByView(spec))
             .description("Number of WorkItems currently in this queue")
             .tag("queue", spec.name())
             .register(registry);
    }
}

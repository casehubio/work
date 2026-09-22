package io.casehub.work.queues.service;

import java.time.Instant;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.work.api.WorkItem;
import io.casehub.work.api.WorkItemLifecycleEvent;
import io.casehub.work.api.WorkItemStatus;
import io.casehub.work.api.spi.WorkItemOperations;
import io.casehub.work.api.spi.WorkItemQueueStateApi;
import io.casehub.work.api.spi.WorkItemStore;
import io.casehub.work.api.view.RelinquishableRequest;
import io.casehub.work.api.view.RelinquishableResult;
import io.casehub.work.api.view.WorkItemView;
import io.casehub.work.queues.model.WorkItemQueueState;
import io.casehub.work.queues.repository.QueueStateStore;
import io.casehub.work.rest.service.ViewMapper;
import io.casehub.work.runtime.event.WorkItemLifecycleEmitter;

@ApplicationScoped
public class DefaultWorkItemQueueStateApi implements WorkItemQueueStateApi {

    @Inject
    WorkItemStore workItemStore;

    @Inject
    QueueStateStore stateStore;

    @Inject
    WorkItemOperations workItemService;

    @Inject
    WorkItemLifecycleEmitter lifecycleEmitter;

    @Override
    public WorkItemView pickup(UUID workItemId, String claimant, String tenancyId) {
        final WorkItem wi = workItemStore.get(workItemId).orElse(null);
        if (wi == null) {
            return null;
        }

        if (wi.status() == WorkItemStatus.PENDING) {
            return ViewMapper.toView(workItemService.claim(workItemId, claimant));
        }

        if (wi.status() == WorkItemStatus.ASSIGNED) {
            final var state = stateStore.get(workItemId).orElse(null);
            if (state == null || !state.relinquishable) {
                throw new IllegalStateException(
                        "WorkItem is ASSIGNED but not marked as relinquishable. "
                                + "Current assignee must call setRelinquishable first.");
            }
            final var updated = wi.toBuilder().assigneeId(claimant).assignedAt(Instant.now()).build();
            final var saved = workItemStore.put(updated);
            state.relinquishable = false;
            lifecycleEmitter.emit(WorkItemLifecycleEvent.of("ASSIGNED", saved, claimant,
                    "Queue pickup (relinquishable takeover)"));
            return ViewMapper.toView(saved);
        }

        throw new IllegalStateException("Cannot pick up WorkItem in status: " + wi.status());
    }

    @Override
    public RelinquishableResult setRelinquishable(UUID workItemId, RelinquishableRequest request,
                                                   String tenancyId) {
        if (workItemStore.get(workItemId).isEmpty()) {
            return null;
        }
        final WorkItemQueueState state = stateStore.findOrCreate(workItemId);
        state.relinquishable = request.relinquishable();
        return new RelinquishableResult(workItemId, state.relinquishable);
    }
}

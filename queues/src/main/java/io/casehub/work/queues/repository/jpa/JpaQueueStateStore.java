package io.casehub.work.queues.repository.jpa;

import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.queues.model.WorkItemQueueState;
import io.casehub.work.queues.repository.QueueStateStore;

/**
 * Default JPA/Panache implementation of {@link QueueStateStore}.
 *
 * <p>Every query is scoped to the current tenant via {@link CurrentPrincipal#tenancyId()}.
 * The {@link #put} method stamps {@code tenancyId} from the principal on insert when
 * the entity does not already carry one.
 */
@ApplicationScoped
public class JpaQueueStateStore implements QueueStateStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemQueueState put(final WorkItemQueueState state) {
        if (state.tenancyId == null) {
            state.tenancyId = currentPrincipal.tenancyId();
        }
        state.persistAndFlush();
        return state;
    }

    @Override
    public Optional<WorkItemQueueState> get(final UUID workItemId) {
        return WorkItemQueueState.find("workItemId = ?1 AND tenancyId = ?2",
                workItemId, currentPrincipal.tenancyId())
                .firstResultOptional();
    }

    @Override
    public WorkItemQueueState findOrCreate(final UUID workItemId) {
        return get(workItemId).orElseGet(() -> {
            final WorkItemQueueState s = new WorkItemQueueState();
            s.workItemId = workItemId;
            s.tenancyId = currentPrincipal.tenancyId();
            s.persistAndFlush();
            return s;
        });
    }
}

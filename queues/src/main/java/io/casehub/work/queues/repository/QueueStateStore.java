package io.casehub.work.queues.repository;

import java.util.Optional;
import java.util.UUID;

import io.casehub.work.queues.model.WorkItemQueueState;

/**
 * Store SPI for {@link WorkItemQueueState} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 */
public interface QueueStateStore {

    /**
     * Persist or update a queue state and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param state the queue state to persist; must not be {@code null}
     * @return the persisted queue state
     */
    WorkItemQueueState put(WorkItemQueueState state);

    /**
     * Retrieve a queue state by its WorkItem primary key, scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID (primary key)
     * @return an {@link Optional} containing the queue state, or empty if not found
     */
    Optional<WorkItemQueueState> get(UUID workItemId);

    /**
     * Find or create a queue state for the given WorkItem, scoped to the current tenant.
     * If no state exists, one is created with defaults and persisted.
     *
     * @param workItemId the WorkItem UUID
     * @return the existing or newly created queue state; never null
     */
    WorkItemQueueState findOrCreate(UUID workItemId);
}

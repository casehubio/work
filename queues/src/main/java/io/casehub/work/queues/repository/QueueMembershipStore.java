package io.casehub.work.queues.repository;

import java.util.List;
import java.util.UUID;

import io.casehub.work.queues.model.WorkItemQueueMembership;

/**
 * Store SPI for {@link WorkItemQueueMembership} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 */
public interface QueueMembershipStore {

    /**
     * Persist or update a membership record and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param membership the membership to persist; must not be {@code null}
     * @return the persisted membership
     */
    WorkItemQueueMembership put(WorkItemQueueMembership membership);

    /**
     * Return all membership rows for the given WorkItem, scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID
     * @return list of membership rows; may be empty, never null
     */
    List<WorkItemQueueMembership> findByWorkItemId(UUID workItemId);

    /**
     * Delete all membership rows for the given WorkItem, scoped to the current tenant.
     *
     * @param workItemId the WorkItem UUID
     */
    void deleteByWorkItemId(UUID workItemId);
}

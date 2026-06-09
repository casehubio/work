package io.casehub.work.queues.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.casehub.work.queues.model.WorkItemFilter;

/**
 * Store SPI for {@link WorkItemFilter} persistence.
 *
 * <p>All queries are scoped to the current tenant via the implementation's
 * {@link io.casehub.platform.api.identity.CurrentPrincipal}.
 */
public interface WorkItemFilterStore {

    /**
     * Persist or update a filter and return the saved instance.
     * Stamps {@code tenancyId} from the current principal on insert when null.
     *
     * @param filter the filter to persist; must not be {@code null}
     * @return the persisted filter
     */
    WorkItemFilter put(WorkItemFilter filter);

    /**
     * Retrieve a filter by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return an {@link Optional} containing the filter, or empty if not found
     */
    Optional<WorkItemFilter> get(UUID id);

    /**
     * Return all active non-lambda filters for the current tenant.
     *
     * @return list of active filters; may be empty, never null
     */
    List<WorkItemFilter> findActive();

    /**
     * Return all filters for the current tenant.
     *
     * @return unordered list of all filters; never null
     */
    List<WorkItemFilter> scanAll();

    /**
     * Delete a filter by its primary key, scoped to the current tenant.
     *
     * @param id the UUID primary key
     * @return {@code true} if the entity was deleted, {@code false} if not found
     */
    boolean delete(UUID id);
}

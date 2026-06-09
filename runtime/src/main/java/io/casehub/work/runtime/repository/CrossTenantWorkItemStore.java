package io.casehub.work.runtime.repository;

import java.util.List;

import io.casehub.work.runtime.model.WorkItem;

/**
 * Cross-tenant {@link WorkItem} store for system-level operations.
 *
 * <p>Unlike the tenant-scoped {@link WorkItemStore}, this store bypasses
 * all tenant filtering and returns items from all tenants.  Only inject
 * this via {@code @CrossTenant} in system-level services (background jobs,
 * admin endpoints).
 */
public interface CrossTenantWorkItemStore {

    /**
     * Finds all active WorkItems (non-terminal statuses) that have deadlines
     * (either {@code expiresAt} or {@code claimDeadline} is non-null).
     *
     * <p>Used by timer/expiry jobs to scan across all tenants for items
     * requiring deadline enforcement.
     *
     * @return list of active WorkItems with deadlines, from all tenants
     */
    List<WorkItem> findActiveWithDeadlines();
}

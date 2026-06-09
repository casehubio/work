package io.casehub.work.runtime.repository;

import java.util.List;

import io.casehub.work.runtime.model.WorkItemSchedule;

/**
 * Cross-tenant {@link WorkItemSchedule} store for system-level operations.
 *
 * <p>Unlike the tenant-scoped {@link WorkItemScheduleStore}, this store bypasses
 * all tenant filtering and returns schedules from all tenants.  Only inject
 * this via {@code @CrossTenant} in system-level services (background jobs,
 * admin endpoints).
 */
public interface CrossTenantWorkItemScheduleStore {

    /**
     * Finds all active schedules across all tenants.
     *
     * <p>Used by timer/scheduling jobs to process recurring WorkItem creation
     * for all tenants in a single pass.
     *
     * @return list of active schedules, from all tenants
     */
    List<WorkItemSchedule> findActive();
}

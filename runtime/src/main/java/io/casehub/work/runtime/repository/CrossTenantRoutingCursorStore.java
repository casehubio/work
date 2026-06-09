package io.casehub.work.runtime.repository;

import java.time.Instant;

/**
 * Cross-tenant {@link io.casehub.work.runtime.model.RoutingCursor} store for
 * system-level operations.
 *
 * <p>Unlike the tenant-scoped {@link RoutingCursorStore}, this store bypasses
 * all tenant filtering and operates across all tenants.  Only inject this via
 * {@code @CrossTenant} in system-level services (background jobs, admin endpoints).
 */
public interface CrossTenantRoutingCursorStore {

    /**
     * Deletes all cursor rows whose {@code lastAccessed} timestamp is older
     * than the specified cutoff, across all tenants.
     *
     * <p>Used by {@link io.casehub.work.runtime.strategy.RoutingCursorCleanupJob}
     * to reclaim stale cursor rows from all tenants in a single pass.
     *
     * @param cutoff instant before which cursors are considered stale
     * @return number of rows deleted
     */
    long cleanupStale(Instant cutoff);
}

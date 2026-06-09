package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.repository.CrossTenant;
import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;
import io.casehub.work.runtime.repository.jpa.JpaCrossTenantRoutingCursorStore;
import io.casehub.work.runtime.repository.jpa.JpaCrossTenantWorkItemScheduleStore;
import io.casehub.work.runtime.repository.jpa.JpaCrossTenantWorkItemStore;

/**
 * CDI producer for {@code @CrossTenant} store variants.
 *
 * <p>Each {@code @Produces} method validates that the active
 * {@code CurrentPrincipal} is the system principal (qualified with
 * {@link WorkSystem}) and that {@code isCrossTenantAdmin()} returns
 * {@code true}. This prevents accidental injection of cross-tenant
 * stores in request-scoped contexts where tenant isolation is required.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 *   @Inject @WorkSystem CurrentPrincipal systemPrincipal;
 *   @Inject @CrossTenant CrossTenantWorkItemStore crossTenantStore;
 * }</pre>
 *
 * <p>Only system-level services (background jobs, timer callbacks,
 * admin endpoints running in a {@link TenantContextRunner} block)
 * should inject {@code @CrossTenant} stores.
 */
@ApplicationScoped
public class CrossTenantProducer {

    @Inject
    @WorkSystem
    CurrentPrincipal systemPrincipal;

    @Inject
    JpaCrossTenantWorkItemStore crossTenantWorkItemStore;

    @Inject
    JpaCrossTenantWorkItemScheduleStore crossTenantScheduleStore;

    @Inject
    JpaCrossTenantRoutingCursorStore crossTenantCursorStore;

    /**
     * Produces a {@code @CrossTenant} {@link CrossTenantWorkItemStore}.
     *
     * @return cross-tenant WorkItem store
     * @throws IllegalStateException if system principal is not a cross-tenant admin
     */
    @Produces
    @CrossTenant
    @ApplicationScoped
    public CrossTenantWorkItemStore produceWorkItemStore() {
        validateSystemPrincipal();
        return crossTenantWorkItemStore;
    }

    /**
     * Produces a {@code @CrossTenant} {@link CrossTenantWorkItemScheduleStore}.
     *
     * @return cross-tenant WorkItemSchedule store
     * @throws IllegalStateException if system principal is not a cross-tenant admin
     */
    @Produces
    @CrossTenant
    @ApplicationScoped
    public CrossTenantWorkItemScheduleStore produceScheduleStore() {
        validateSystemPrincipal();
        return crossTenantScheduleStore;
    }

    /**
     * Produces a {@code @CrossTenant} {@link CrossTenantRoutingCursorStore}.
     *
     * @return cross-tenant RoutingCursor store
     * @throws IllegalStateException if system principal is not a cross-tenant admin
     */
    @Produces
    @CrossTenant
    @ApplicationScoped
    public CrossTenantRoutingCursorStore produceCursorStore() {
        validateSystemPrincipal();
        return crossTenantCursorStore;
    }

    /**
     * Validates that the system principal has cross-tenant admin privileges.
     *
     * @throws IllegalStateException if {@code isCrossTenantAdmin()} returns false
     */
    private void validateSystemPrincipal() {
        if (!systemPrincipal.isCrossTenantAdmin()) {
            throw new IllegalStateException(
                "SystemCurrentPrincipal.isCrossTenantAdmin() must return true");
        }
    }
}

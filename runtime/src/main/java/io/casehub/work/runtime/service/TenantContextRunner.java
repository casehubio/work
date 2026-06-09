package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

/**
 * Establishes a per-tenant CDI request context for async/background work.
 *
 * <p>Activates a new request scope (if one is not already active),
 * configures {@link TenantHolder} with the given tenancy identity,
 * runs the supplied work, and tears down the context.
 *
 * <p>Within the activated context, {@code CurrentPrincipal.tenancyId()}
 * returns the supplied {@code tenancyId} (via {@link TenantScopedPrincipal}
 * which delegates to {@link TenantHolder}).
 *
 * <h4>Usage</h4>
 * <pre>{@code
 *   tenantContextRunner.runInTenantContext("tenant-abc", () -> {
 *       // CurrentPrincipal.tenancyId() == "tenant-abc"
 *       store.put(workItem);
 *   });
 * }</pre>
 */
@ApplicationScoped
public class TenantContextRunner {

    /**
     * Run {@code work} in a request context scoped to the given tenant.
     *
     * @param tenancyId  the tenant identity to establish
     * @param work       the unit of work to execute
     */
    public void runInTenantContext(String tenancyId, Runnable work) {
        runInTenantContext(tenancyId, "system", work);
    }

    /**
     * Run {@code work} in a request context scoped to the given tenant and actor.
     *
     * @param tenancyId  the tenant identity to establish
     * @param actorId    the actor identity to establish
     * @param work       the unit of work to execute
     */
    public void runInTenantContext(String tenancyId, String actorId, Runnable work) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean alreadyActive = requestContext.isActive();

        if (!alreadyActive) {
            requestContext.activate();
        }
        try {
            TenantHolder holder = Arc.container().instance(TenantHolder.class).get();
            String previousTenancyId = alreadyActive ? holder.getTenancyId() : null;
            String previousActorId = alreadyActive ? holder.getActorId() : null;

            holder.setTenancyId(tenancyId);
            holder.setActorId(actorId);
            try {
                work.run();
            } finally {
                if (alreadyActive) {
                    holder.setTenancyId(previousTenancyId);
                    holder.setActorId(previousActorId);
                }
            }
        } finally {
            if (!alreadyActive) {
                requestContext.terminate();
            }
        }
    }
}

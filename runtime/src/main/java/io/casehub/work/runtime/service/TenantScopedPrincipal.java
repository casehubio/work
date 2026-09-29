package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;

import io.casehub.platform.api.identity.CurrentPrincipal;

/**
 * Request-scoped {@link CurrentPrincipal} that delegates to {@link TenantHolder}.
 *
 * <p>{@code @Alternative @Priority(150)} beats the platform's
 * {@code MockCurrentPrincipal} ({@code @Priority(100)}) from
 * {@code DefaultBeans} but loses to test beans at higher priority.
 *
 * <p>In REST request contexts where nobody touches {@link TenantHolder},
 * the defaults (actorId = "system", tenancyId = default UUID) match
 * {@code MockCurrentPrincipal} behaviour.  In {@link TenantContextRunner}
 * contexts the holder is set to the target tenant before the work runs.
 */
@RequestScoped
@Alternative
@Priority(150)
@Unremovable
public class TenantScopedPrincipal implements CurrentPrincipal {

    @Inject
    TenantHolder holder;

    @Override
    public String actorId() {
        return holder.getActorId();
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return holder.getTenancyId();
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return false;
    }
}

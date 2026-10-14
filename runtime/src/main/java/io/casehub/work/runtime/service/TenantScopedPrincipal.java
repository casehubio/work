package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;

import io.casehub.platform.api.identity.CurrentPrincipal;

/**
 * Request-scoped {@link CurrentPrincipal} that delegates to {@link TenantHolder}.
 *
 * <p>{@code @DefaultBean} makes this a fallback — any application-provided
 * {@link CurrentPrincipal} takes precedence automatically.
 *
 * <p>In REST request contexts where nobody touches {@link TenantHolder},
 * the defaults (actorId = "system", tenancyId = default UUID) match
 * {@code MockCurrentPrincipal} behaviour.  In {@link TenantContextRunner}
 * contexts the holder is set to the target tenant before the work runs.
 */
@RequestScoped
@DefaultBean
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

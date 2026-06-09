package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import io.quarkus.arc.Unremovable;

import io.casehub.platform.api.identity.CurrentPrincipal;

/**
 * Request-scoped {@link CurrentPrincipal} that delegates to {@link TenantHolder}.
 *
 * <p>This bean is <strong>not</strong> {@code @DefaultBean} and not
 * {@code @Alternative} — it is a plain (normal-priority) bean.  In CDI
 * resolution it beats {@code MockCurrentPrincipal} ({@code @DefaultBean})
 * from casehub-platform in any context where the request scope is active.
 *
 * <p>In REST request contexts where nobody touches {@link TenantHolder},
 * the defaults (actorId = "system", tenancyId = default UUID) match
 * {@code MockCurrentPrincipal} behaviour.  In {@link TenantContextRunner}
 * contexts the holder is set to the target tenant before the work runs.
 */
@RequestScoped
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

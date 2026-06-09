package io.casehub.work.runtime.service;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;

/**
 * System-level {@link CurrentPrincipal} for background/internal operations.
 *
 * <p>Qualified with {@link WorkSystem} so it never competes with the
 * request-scoped or mock principal for the default (unqualified)
 * {@code CurrentPrincipal} injection point.  Inject as:
 * <pre>{@code
 *   @Inject @WorkSystem CurrentPrincipal systemPrincipal;
 * }</pre>
 */
@ApplicationScoped
@WorkSystem
public class SystemCurrentPrincipal implements CurrentPrincipal {

    @Override
    public String actorId() {
        return "system";
    }

    @Override
    public Set<String> groups() {
        return Set.of();
    }

    @Override
    public String tenancyId() {
        return TenancyConstants.DEFAULT_TENANT_ID;
    }

    @Override
    public boolean isCrossTenantAdmin() {
        return true;
    }
}

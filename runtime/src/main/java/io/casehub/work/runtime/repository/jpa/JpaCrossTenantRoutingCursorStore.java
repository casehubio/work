package io.casehub.work.runtime.repository.jpa;

import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import io.casehub.work.runtime.model.RoutingCursor;
import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;

/**
 * Cross-tenant JPA implementation of {@link CrossTenantRoutingCursorStore}.
 *
 * <p>Does NOT inject {@link io.casehub.platform.api.identity.CurrentPrincipal}
 * and does NOT filter by {@code tenancyId} — queries operate on cursors from all tenants.
 * Only injected into system-level services via the {@code @CrossTenant} qualifier.
 */
@ApplicationScoped
public class JpaCrossTenantRoutingCursorStore implements CrossTenantRoutingCursorStore {

    @Override
    @Transactional
    public long cleanupStale(Instant cutoff) {
        return RoutingCursor.delete("lastAccessed < ?1", cutoff);
    }
}

package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.Date;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.bson.Document;

import io.casehub.work.runtime.repository.CrossTenantRoutingCursorStore;

/**
 * Cross-tenant MongoDB implementation of {@link CrossTenantRoutingCursorStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * Deletes stale cursor rows across all tenants without a {@code tenancyId} filter.
 * Only injected via the {@code @CrossTenant} qualifier in system-level services.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoCrossTenantRoutingCursorStore implements CrossTenantRoutingCursorStore {

    @Override
    public long cleanupStale(Instant cutoff) {
        return MongoRoutingCursorDocument.delete(
                new Document("lastAccessed", new Document("$lt", Date.from(cutoff))));
    }
}

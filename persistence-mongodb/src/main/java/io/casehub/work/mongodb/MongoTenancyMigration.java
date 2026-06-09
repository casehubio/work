package io.casehub.work.mongodb;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.casehub.platform.api.identity.TenancyConstants;
import io.quarkus.runtime.StartupEvent;

/**
 * Backfills {@code tenancyId} on existing MongoDB documents on startup.
 *
 * <p>
 * Updates all WorkItem and AuditEntry documents that are missing {@code tenancyId},
 * stamping them with {@link TenancyConstants#DEFAULT_TENANT_ID}. Runs once on
 * startup. Idempotent — safe to run multiple times (only updates documents where
 * {@code tenancyId} does not exist).
 */
@ApplicationScoped
public class MongoTenancyMigration {

    @Inject
    MongoClient mongoClient;

    void onStartup(@Observes final StartupEvent event) {
        final MongoDatabase db = mongoClient.getDatabase("workitems");
        for (final String collection : List.of("work_items", "audit_entries")) {
            final long updated = db.getCollection(collection).updateMany(
                    Filters.exists("tenancyId", false),
                    Updates.set("tenancyId", TenancyConstants.DEFAULT_TENANT_ID)
            ).getModifiedCount();
            if (updated > 0) {
                System.out.printf("[MongoTenancyMigration] Backfilled tenancyId on %d documents in %s%n",
                        updated, collection);
            }
        }
    }
}

package io.casehub.work.mongodb.core;

import java.util.List;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import io.casehub.platform.api.identity.TenancyConstants;

public class MongoTenancyMigration {

    private final MongoDatabase database;

    public MongoTenancyMigration(MongoDatabase database) {
        this.database = database;
    }

    public void run() {
        for (final String collection : List.of("work_items", "audit_entries")) {
            final long updated = database.getCollection(collection).updateMany(
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

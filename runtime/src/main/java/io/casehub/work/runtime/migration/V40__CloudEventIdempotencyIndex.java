package io.casehub.work.runtime.migration;

import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jboss.logging.Logger;

public class V40__CloudEventIdempotencyIndex extends BaseJavaMigration {

    private static final Logger LOG = Logger.getLogger(V40__CloudEventIdempotencyIndex.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final String dbProduct = context.getConnection().getMetaData().getDatabaseProductName();

        if ("PostgreSQL".equalsIgnoreCase(dbProduct)) {
            try (Statement stmt = context.getConnection().createStatement()) {
                stmt.execute(
                    "CREATE UNIQUE INDEX uq_workitem_cloudevent_idempotency "
                  + "ON work_item (caller_ref, tenancy_id) "
                  + "WHERE created_by LIKE 'cloudevent:%'");
            }
            LOG.info("V40: Created partial unique index uq_workitem_cloudevent_idempotency (PostgreSQL)");
        } else {
            LOG.infof("V40: Skipping partial unique index — %s does not support WHERE clause on indexes. "
                     + "Application-level idempotency via findByCallerRef is active on all databases.", dbProduct);
        }
    }
}

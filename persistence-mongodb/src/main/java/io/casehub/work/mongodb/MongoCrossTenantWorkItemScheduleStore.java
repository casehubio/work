package io.casehub.work.mongodb;

import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.bson.Document;

import io.casehub.work.runtime.model.WorkItemSchedule;
import io.casehub.work.runtime.repository.CrossTenantWorkItemScheduleStore;

/**
 * Cross-tenant MongoDB implementation of {@link CrossTenantWorkItemScheduleStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * Queries omit the {@code tenancyId} filter, returning results from all tenants.
 * Only injected via the {@code @CrossTenant} qualifier in system-level services.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoCrossTenantWorkItemScheduleStore implements CrossTenantWorkItemScheduleStore {

    @Override
    public List<WorkItemSchedule> findActive() {
        Document filter = new Document("active", true);

        return MongoWorkItemScheduleDocument.<MongoWorkItemScheduleDocument>find(filter).list()
                .stream()
                .map(MongoWorkItemScheduleDocument::toDomain)
                .toList();
    }
}

package io.casehub.work.mongodb;

import java.util.Arrays;
import java.util.List;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import org.bson.Document;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.CrossTenantWorkItemStore;

/**
 * Cross-tenant MongoDB implementation of {@link CrossTenantWorkItemStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * Queries omit the {@code tenancyId} filter, returning results from all tenants.
 * Only injected via the {@code @CrossTenant} qualifier in system-level services.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoCrossTenantWorkItemStore implements CrossTenantWorkItemStore {

    private static final List<String> TERMINAL_STATUSES =
            Arrays.stream(WorkItemStatus.values())
                    .filter(WorkItemStatus::isTerminal)
                    .map(Enum::name)
                    .toList();

    @Override
    public List<WorkItem> findActiveWithDeadlines() {
        Document filter = new Document("$and", List.of(
                new Document("status", new Document("$nin", TERMINAL_STATUSES)),
                new Document("$or", List.of(
                        new Document("expiresAt", new Document("$ne", null)),
                        new Document("claimDeadline", new Document("$ne", null))))));

        return MongoWorkItemDocument.<MongoWorkItemDocument>find(filter).list()
                .stream()
                .map(MongoWorkItemDocument::toDomain)
                .toList();
    }
}

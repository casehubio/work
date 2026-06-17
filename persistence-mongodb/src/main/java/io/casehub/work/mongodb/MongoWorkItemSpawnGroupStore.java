package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;

/**
 * MongoDB implementation of {@link WorkItemSpawnGroupStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 *
 * <p>
 * The {@link #put(WorkItemSpawnGroup)} method implements optimistic concurrency control
 * via MongoDB's {@code findOneAndUpdate} with a version field filter. This prevents
 * race conditions when updating M-of-N counters in clustered deployments.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemSpawnGroupStore implements WorkItemSpawnGroupStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Inject
    MongoClient mongoClient;

    @Override
    public WorkItemSpawnGroup put(final WorkItemSpawnGroup group) {
        // Assign defaults for new entities
        if (group.id == null) {
            group.id = UUID.randomUUID();
        }
        if (group.createdAt == null) {
            group.createdAt = Instant.now();
        }
        if (group.tenancyId == null) {
            group.tenancyId = currentPrincipal.tenancyId();
        }

        final String idStr = group.id.toString();

        // Check if this is a new document or an update
        final boolean exists = MongoWorkItemSpawnGroupDocument.find(
                new Document("_id", idStr)).firstResult() != null;

        if (!exists) {
            // New document — plain persist with version 0
            group.version = 0L;
            MongoWorkItemSpawnGroupDocument.from(group).persist();
        } else {
            // Existing document — findOneAndUpdate with version check (atomic OCC)
            final Bson filter = Filters.and(
                    Filters.eq("_id", idStr),
                    Filters.eq("version", group.version));

            final Bson update = Updates.combine(
                    Updates.set("tenancyId", group.tenancyId),
                    Updates.set("parentId", group.parentId != null ? group.parentId.toString() : null),
                    Updates.set("idempotencyKey", group.idempotencyKey),
                    Updates.set("createdAt", group.createdAt),
                    Updates.set("instanceCount", group.instanceCount),
                    Updates.set("requiredCount", group.requiredCount),
                    Updates.set("onThresholdReached", group.onThresholdReached),
                    Updates.set("allowSameAssignee", group.allowSameAssignee),
                    Updates.set("parentRole", group.parentRole),
                    Updates.set("completedCount", group.completedCount),
                    Updates.set("rejectedCount", group.rejectedCount),
                    Updates.set("policyTriggered", group.policyTriggered),
                    Updates.inc("version", 1L));

            final Document result = mongoClient.getDatabase("workitems")
                    .getCollection("work_item_spawn_groups")
                    .findOneAndUpdate(filter, update,
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

            if (result == null) {
                throw new OptimisticLockException(
                        "Version conflict on WorkItemSpawnGroup " + idStr);
            }

            group.version = result.getLong("version");
        }
        return group;
    }

    @Override
    public Optional<WorkItemSpawnGroup> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemSpawnGroupDocument doc = MongoWorkItemSpawnGroupDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemSpawnGroupDocument> docs = MongoWorkItemSpawnGroupDocument.<MongoWorkItemSpawnGroupDocument> find(
                filter).list();
        return docs.stream()
                .map(MongoWorkItemSpawnGroupDocument::toDomain)
                .sorted(Comparator.comparing((WorkItemSpawnGroup g) -> g.createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<WorkItemSpawnGroup> findByParentAndKey(final UUID parentId, final String groupKey) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("idempotencyKey", groupKey)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemSpawnGroupDocument doc = MongoWorkItemSpawnGroupDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("requiredCount", new Document("$ne", null));
        final MongoWorkItemSpawnGroupDocument doc = MongoWorkItemSpawnGroupDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemSpawnGroupDocument.delete(filter) > 0;
    }
}

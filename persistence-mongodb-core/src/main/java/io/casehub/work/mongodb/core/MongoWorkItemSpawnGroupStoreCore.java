package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemSpawnGroupDocument;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.repository.WorkItemSpawnGroupStore;
import jakarta.persistence.OptimisticLockException;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemSpawnGroupStoreCore implements WorkItemSpawnGroupStore {

    private final MongoCollection<MongoWorkItemSpawnGroupDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemSpawnGroupStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_spawn_groups", MongoWorkItemSpawnGroupDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public WorkItemSpawnGroup put(final WorkItemSpawnGroup group) {
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

        final boolean exists = collection.find(new Document("_id", idStr)).first() != null;

        if (!exists) {
            group.version = 0L;
            collection.insertOne(MongoWorkItemSpawnGroupDocument.from(group));
        } else {
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
                    Updates.set("groupStatus", group.groupStatus != null ? group.groupStatus.name() : null),
                    Updates.inc("version", 1L));

            final MongoWorkItemSpawnGroupDocument result = collection
                    .findOneAndUpdate(filter, update,
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

            if (result == null) {
                throw new OptimisticLockException(
                        "Version conflict on WorkItemSpawnGroup " + idStr);
            }

            group.version = result.version;
        }
        return group;
    }

    @Override
    public Optional<WorkItemSpawnGroup> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemSpawnGroupDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public List<WorkItemSpawnGroup> findByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemSpawnGroupDocument::toDomain)
                .sorted(Comparator.comparing((WorkItemSpawnGroup g) -> g.createdAt).reversed())
                .toList();
    }

    @Override
    public Optional<WorkItemSpawnGroup> findByParentAndKey(final UUID parentId, final String groupKey) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("idempotencyKey", groupKey)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemSpawnGroupDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public Optional<WorkItemSpawnGroup> findMultiInstanceByParentId(final UUID parentId) {
        final Document filter = new Document("parentId", parentId.toString())
                .append("tenancyId", currentPrincipal.tenancyId())
                .append("requiredCount", new Document("$ne", null));
        final MongoWorkItemSpawnGroupDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemSpawnGroupDocument::toDomain);
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

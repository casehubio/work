package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemSpawnGroup}.
 *
 * <p>
 * Stored in the {@code work_item_spawn_groups} collection. Converted to and from the domain
 * {@link WorkItemSpawnGroup} by {@link MongoWorkItemSpawnGroupStore}.
 */
@MongoEntity(collection = "work_item_spawn_groups")
public class MongoWorkItemSpawnGroupDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String parentId;
    public String idempotencyKey;
    public Instant createdAt;
    public Long version;
    public Integer instanceCount;
    public Integer requiredCount;
    public String onThresholdReached;
    public boolean allowSameAssignee;
    public String parentRole;
    public int completedCount;
    public int rejectedCount;
    public boolean policyTriggered;
    public String groupStatus;

    /** Convert a domain {@link WorkItemSpawnGroup} to a MongoDB document. */
    public static MongoWorkItemSpawnGroupDocument from(final WorkItemSpawnGroup group) {
        final MongoWorkItemSpawnGroupDocument doc = new MongoWorkItemSpawnGroupDocument();
        doc.id = group.id != null ? group.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = group.tenancyId;
        doc.parentId = group.parentId != null ? group.parentId.toString() : null;
        doc.idempotencyKey = group.idempotencyKey;
        doc.createdAt = group.createdAt;
        doc.version = group.version;
        doc.instanceCount = group.instanceCount;
        doc.requiredCount = group.requiredCount;
        doc.onThresholdReached = group.onThresholdReached;
        doc.allowSameAssignee = group.allowSameAssignee;
        doc.parentRole = group.parentRole;
        doc.completedCount = group.completedCount;
        doc.rejectedCount = group.rejectedCount;
        doc.policyTriggered = group.policyTriggered;
        doc.groupStatus = group.groupStatus != null ? group.groupStatus.name() : null;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemSpawnGroup}. */
    public WorkItemSpawnGroup toDomain() {
        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.id = UUID.fromString(id);
        group.tenancyId = tenancyId;
        group.parentId = parentId != null ? UUID.fromString(parentId) : null;
        group.idempotencyKey = idempotencyKey;
        group.createdAt = createdAt;
        group.version = version;
        group.instanceCount = instanceCount;
        group.requiredCount = requiredCount;
        group.onThresholdReached = onThresholdReached;
        group.allowSameAssignee = allowSameAssignee;
        group.parentRole = parentRole;
        group.completedCount = completedCount;
        group.rejectedCount = rejectedCount;
        group.policyTriggered = policyTriggered;
        group.groupStatus = groupStatus != null ? GroupStatus.valueOf(groupStatus) : null;
        return group;
    }
}

package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemRelation;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemRelation}.
 *
 * <p>
 * Stored in the {@code work_item_relations} collection. Converted to and from the domain
 * {@link WorkItemRelation} by {@link MongoWorkItemRelationStore}.
 */
@MongoEntity(collection = "work_item_relations")
public class MongoWorkItemRelationDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String sourceId;
    public String targetId;
    public String relationType;
    public String createdBy;
    public Instant createdAt;

    /** Convert a domain {@link WorkItemRelation} to a MongoDB document. */
    public static MongoWorkItemRelationDocument from(final WorkItemRelation relation) {
        final MongoWorkItemRelationDocument doc = new MongoWorkItemRelationDocument();
        doc.id = relation.id != null ? relation.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = relation.tenancyId;
        doc.sourceId = relation.sourceId != null ? relation.sourceId.toString() : null;
        doc.targetId = relation.targetId != null ? relation.targetId.toString() : null;
        doc.relationType = relation.relationType;
        doc.createdBy = relation.createdBy;
        doc.createdAt = relation.createdAt;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemRelation}. */
    public WorkItemRelation toDomain() {
        final WorkItemRelation relation = new WorkItemRelation();
        relation.id = UUID.fromString(id);
        relation.tenancyId = tenancyId;
        relation.sourceId = sourceId != null ? UUID.fromString(sourceId) : null;
        relation.targetId = targetId != null ? UUID.fromString(targetId) : null;
        relation.relationType = relationType;
        relation.createdBy = createdBy;
        relation.createdAt = createdAt;
        return relation;
    }
}

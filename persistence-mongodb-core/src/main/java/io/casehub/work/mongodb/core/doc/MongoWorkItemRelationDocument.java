package io.casehub.work.mongodb.core.doc;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemRelation;

public class MongoWorkItemRelationDocument {

    @BsonId
    public String id;

    public String tenancyId;
    public String sourceId;
    public String targetId;
    public String relationType;
    public String createdBy;
    public Instant createdAt;

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

package io.casehub.work.mongodb.core.doc;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemLink;

public class MongoWorkItemLinkDocument {

    @BsonId
    public String id;

    public String tenancyId;
    public String workItemId;
    public String url;
    public String title;
    public String relationType;
    public String linkedBy;
    public Instant createdAt;

    public static MongoWorkItemLinkDocument from(final WorkItemLink link) {
        final MongoWorkItemLinkDocument doc = new MongoWorkItemLinkDocument();
        doc.id = link.id != null ? link.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = link.tenancyId;
        doc.workItemId = link.workItemId != null ? link.workItemId.toString() : null;
        doc.url = link.url;
        doc.title = link.title;
        doc.relationType = link.relationType;
        doc.linkedBy = link.linkedBy;
        doc.createdAt = link.createdAt;
        return doc;
    }

    public WorkItemLink toDomain() {
        final WorkItemLink link = new WorkItemLink();
        link.id = UUID.fromString(id);
        link.tenancyId = tenancyId;
        link.workItemId = workItemId != null ? UUID.fromString(workItemId) : null;
        link.url = url;
        link.title = title;
        link.relationType = relationType;
        link.linkedBy = linkedBy;
        link.createdAt = createdAt;
        return link;
    }
}

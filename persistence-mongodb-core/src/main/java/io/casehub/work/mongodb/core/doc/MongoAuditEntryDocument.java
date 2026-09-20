package io.casehub.work.mongodb.core.doc;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.AuditEntry;

public class MongoAuditEntryDocument {

    @BsonId
    public String id;

    public String tenancyId;
    public String workItemId;
    public String event;
    public String actor;
    public String detail;
    public Instant occurredAt;

    public static MongoAuditEntryDocument from(final AuditEntry entry) {
        final MongoAuditEntryDocument doc = new MongoAuditEntryDocument();
        doc.id = entry.id != null ? entry.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = entry.tenancyId;
        doc.workItemId = entry.workItemId != null ? entry.workItemId.toString() : null;
        doc.event = entry.event;
        doc.actor = entry.actor;
        doc.detail = entry.detail;
        doc.occurredAt = entry.occurredAt != null ? entry.occurredAt : Instant.now();
        return doc;
    }

    public AuditEntry toDomain() {
        final AuditEntry entry = new AuditEntry();
        entry.id = UUID.fromString(id);
        entry.tenancyId = tenancyId;
        entry.workItemId = workItemId != null ? UUID.fromString(workItemId) : null;
        entry.event = event;
        entry.actor = actor;
        entry.detail = detail;
        entry.occurredAt = occurredAt;
        return entry;
    }
}

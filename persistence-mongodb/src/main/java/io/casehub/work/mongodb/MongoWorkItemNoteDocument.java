package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.UUID;

import org.bson.codecs.pojo.annotations.BsonId;

import io.casehub.work.runtime.model.WorkItemNote;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;

/**
 * MongoDB document representation of a {@link WorkItemNote}.
 *
 * <p>
 * Stored in the {@code work_item_notes} collection. Converted to and from the domain
 * {@link WorkItemNote} by {@link MongoWorkItemNoteStore}.
 */
@MongoEntity(collection = "work_item_notes")
public class MongoWorkItemNoteDocument extends PanacheMongoEntityBase {

    @BsonId
    public String id;

    public String tenancyId;
    public String workItemId;
    public String content;
    public String author;
    public Instant createdAt;
    public Instant editedAt;

    /** Convert a domain {@link WorkItemNote} to a MongoDB document. */
    public static MongoWorkItemNoteDocument from(final WorkItemNote note) {
        final MongoWorkItemNoteDocument doc = new MongoWorkItemNoteDocument();
        doc.id = note.id != null ? note.id.toString() : UUID.randomUUID().toString();
        doc.tenancyId = note.tenancyId;
        doc.workItemId = note.workItemId != null ? note.workItemId.toString() : null;
        doc.content = note.content;
        doc.author = note.author;
        doc.createdAt = note.createdAt;
        doc.editedAt = note.editedAt;
        return doc;
    }

    /** Convert this document back to a domain {@link WorkItemNote}. */
    public WorkItemNote toDomain() {
        final WorkItemNote note = new WorkItemNote();
        note.id = UUID.fromString(id);
        note.tenancyId = tenancyId;
        note.workItemId = workItemId != null ? UUID.fromString(workItemId) : null;
        note.content = content;
        note.author = author;
        note.createdAt = createdAt;
        note.editedAt = editedAt;
        return note;
    }
}

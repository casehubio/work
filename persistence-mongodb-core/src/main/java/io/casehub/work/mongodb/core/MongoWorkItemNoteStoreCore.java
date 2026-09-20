package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemNoteDocument;
import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemNoteStoreCore implements WorkItemNoteStore {

    private final MongoCollection<MongoWorkItemNoteDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemNoteStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_notes", MongoWorkItemNoteDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        if (note.id == null) {
            note.id = UUID.randomUUID();
        }
        if (note.createdAt == null) {
            note.createdAt = Instant.now();
        }
        if (note.tenancyId == null) {
            note.tenancyId = currentPrincipal.tenancyId();
        }

        collection.insertOne(MongoWorkItemNoteDocument.from(note));
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        final Document filter = new Document("_id", noteId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemNoteDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemNoteDocument::toDomain);
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemNoteDocument::toDomain)
                .sorted(Comparator.comparing(n -> n.createdAt))
                .toList();
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        if (note.tenancyId == null) {
            note.tenancyId = currentPrincipal.tenancyId();
        }

        MongoWorkItemNoteDocument doc = MongoWorkItemNoteDocument.from(note);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        final Document filter = new Document("_id", noteId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

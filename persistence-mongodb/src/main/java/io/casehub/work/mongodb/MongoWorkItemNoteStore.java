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

import org.bson.Document;

import io.casehub.platform.api.identity.CurrentPrincipal;

import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * MongoDB implementation of {@link WorkItemNoteStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemNoteStore implements WorkItemNoteStore {

    @Inject
    CurrentPrincipal currentPrincipal;

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

        MongoWorkItemNoteDocument.from(note).persist();
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        final Document filter = new Document("_id", noteId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemNoteDocument doc = MongoWorkItemNoteDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemNoteDocument::toDomain);
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemNoteDocument> docs = MongoWorkItemNoteDocument.<MongoWorkItemNoteDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemNoteDocument::toDomain)
                .sorted(Comparator.comparing(n -> n.createdAt))
                .toList();
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        if (note.tenancyId == null) {
            note.tenancyId = currentPrincipal.tenancyId();
        }

        MongoWorkItemNoteDocument.from(note).persistOrUpdate();
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        final Document filter = new Document("_id", noteId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemNoteDocument.delete(filter) > 0;
    }
}

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

import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;

/**
 * MongoDB implementation of {@link WorkItemRelationStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemRelationStore implements WorkItemRelationStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemRelation put(final WorkItemRelation relation) {
        if (relation.id == null) {
            relation.id = UUID.randomUUID();
        }
        if (relation.createdAt == null) {
            relation.createdAt = Instant.now();
        }
        if (relation.tenancyId == null) {
            relation.tenancyId = currentPrincipal.tenancyId();
        }

        MongoWorkItemRelationDocument.from(relation).persistOrUpdate();
        return relation;
    }

    @Override
    public Optional<WorkItemRelation> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemRelationDocument doc = MongoWorkItemRelationDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemRelationDocument::toDomain);
    }

    @Override
    public List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        final Document filter = new Document("sourceId", sourceId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemRelationDocument> docs = MongoWorkItemRelationDocument
                .<MongoWorkItemRelationDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetId(final UUID targetId) {
        final Document filter = new Document("targetId", targetId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemRelationDocument> docs = MongoWorkItemRelationDocument
                .<MongoWorkItemRelationDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findBySourceAndType(final UUID sourceId, final String type) {
        final Document filter = new Document("sourceId", sourceId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemRelationDocument> docs = MongoWorkItemRelationDocument
                .<MongoWorkItemRelationDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetAndType(final UUID targetId, final String type) {
        final Document filter = new Document("targetId", targetId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemRelationDocument> docs = MongoWorkItemRelationDocument
                .<MongoWorkItemRelationDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public Optional<WorkItemRelation> findExisting(final UUID sourceId, final UUID targetId,
            final String relationType) {
        final Document filter = new Document("sourceId", sourceId.toString())
                .append("targetId", targetId.toString())
                .append("relationType", relationType)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemRelationDocument doc = MongoWorkItemRelationDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemRelationDocument::toDomain);
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemRelationDocument.delete(filter) > 0;
    }
}

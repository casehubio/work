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

import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;

/**
 * MongoDB implementation of {@link WorkItemLinkStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemLinkStore implements WorkItemLinkStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public WorkItemLink put(final WorkItemLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.createdAt == null) {
            link.createdAt = Instant.now();
        }
        if (link.tenancyId == null) {
            link.tenancyId = currentPrincipal.tenancyId();
        }

        MongoWorkItemLinkDocument.from(link).persistOrUpdate();
        return link;
    }

    @Override
    public Optional<WorkItemLink> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemLinkDocument doc = MongoWorkItemLinkDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemLinkDocument> docs = MongoWorkItemLinkDocument.<MongoWorkItemLinkDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemLinkDocument::toDomain)
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemLink> findByWorkItemIdAndType(final UUID workItemId, final String type) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemLinkDocument> docs = MongoWorkItemLinkDocument.<MongoWorkItemLinkDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemLinkDocument::toDomain)
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemLinkDocument.delete(filter) > 0;
    }
}

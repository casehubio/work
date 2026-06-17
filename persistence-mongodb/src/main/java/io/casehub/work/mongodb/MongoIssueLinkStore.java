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

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;

/**
 * MongoDB implementation of {@link IssueLinkStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoIssueLinkStore implements IssueLinkStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoIssueLinkDocument doc = MongoIssueLinkDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoIssueLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoIssueLinkDocument> docs = MongoIssueLinkDocument.<MongoIssueLinkDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoIssueLinkDocument::toDomain)
                .sorted(Comparator.comparing(link -> link.linkedAt))
                .toList();
    }

    @Override
    public Optional<WorkItemIssueLink> findByRef(final UUID workItemId, final String trackerType,
            final String externalRef) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("trackerType", trackerType)
                .append("externalRef", externalRef)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoIssueLinkDocument doc = MongoIssueLinkDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoIssueLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        final Document filter = new Document("trackerType", trackerType)
                .append("externalRef", externalRef)
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoIssueLinkDocument> docs = MongoIssueLinkDocument.<MongoIssueLinkDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoIssueLinkDocument::toDomain)
                .toList();
    }

    @Override
    public WorkItemIssueLink save(final WorkItemIssueLink link) {
        if (link.id == null) {
            link.id = UUID.randomUUID();
        }
        if (link.linkedAt == null) {
            link.linkedAt = Instant.now();
        }
        if (link.tenancyId == null) {
            link.tenancyId = currentPrincipal.tenancyId();
        }

        MongoIssueLinkDocument.from(link).persistOrUpdate();
        return link;
    }

    @Override
    public void delete(final WorkItemIssueLink link) {
        if (link != null && link.tenancyId != null
                && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            final Document filter = new Document("_id", link.id.toString())
                    .append("tenancyId", link.tenancyId);
            MongoIssueLinkDocument.delete(filter);
        }
    }
}

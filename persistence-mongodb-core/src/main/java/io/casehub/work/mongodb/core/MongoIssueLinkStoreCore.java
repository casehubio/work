package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.mongodb.core.doc.MongoIssueLinkDocument;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoIssueLinkStoreCore implements IssueLinkStore {

    private final MongoCollection<MongoIssueLinkDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoIssueLinkStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("issue_links", MongoIssueLinkDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public Optional<WorkItemIssueLink> findById(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoIssueLinkDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoIssueLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemIssueLink> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
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
        final MongoIssueLinkDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoIssueLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemIssueLink> findByTrackerRef(final String trackerType, final String externalRef) {
        final Document filter = new Document("trackerType", trackerType)
                .append("externalRef", externalRef)
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
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

        MongoIssueLinkDocument doc = MongoIssueLinkDocument.from(link);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return link;
    }

    @Override
    public void delete(final WorkItemIssueLink link) {
        if (link != null && link.tenancyId != null
                && currentPrincipal.tenancyId().equals(link.tenancyId)) {
            final Document filter = new Document("_id", link.id.toString())
                    .append("tenancyId", link.tenancyId);
            collection.deleteOne(filter);
        }
    }
}

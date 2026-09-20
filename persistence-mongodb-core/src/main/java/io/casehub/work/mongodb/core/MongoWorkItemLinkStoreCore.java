package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemLinkDocument;
import io.casehub.work.runtime.model.WorkItemLink;
import io.casehub.work.runtime.repository.WorkItemLinkStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemLinkStoreCore implements WorkItemLinkStore {

    private final MongoCollection<MongoWorkItemLinkDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemLinkStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_links", MongoWorkItemLinkDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

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

        MongoWorkItemLinkDocument doc = MongoWorkItemLinkDocument.from(link);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return link;
    }

    @Override
    public Optional<WorkItemLink> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemLinkDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemLinkDocument::toDomain);
    }

    @Override
    public List<WorkItemLink> findByWorkItemId(final UUID workItemId) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemLinkDocument::toDomain)
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemLink> findByWorkItemIdAndType(final UUID workItemId, final String type) {
        final Document filter = new Document("workItemId", workItemId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemLinkDocument::toDomain)
                .sorted(Comparator.comparing(l -> l.createdAt))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

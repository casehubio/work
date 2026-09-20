package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemRelationDocument;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.repository.WorkItemRelationStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemRelationStoreCore implements WorkItemRelationStore {

    private final MongoCollection<MongoWorkItemRelationDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemRelationStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_relations", MongoWorkItemRelationDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

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

        MongoWorkItemRelationDocument doc = MongoWorkItemRelationDocument.from(relation);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return relation;
    }

    @Override
    public Optional<WorkItemRelation> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemRelationDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemRelationDocument::toDomain);
    }

    @Override
    public List<WorkItemRelation> findBySourceId(final UUID sourceId) {
        final Document filter = new Document("sourceId", sourceId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetId(final UUID targetId) {
        final Document filter = new Document("targetId", targetId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findBySourceAndType(final UUID sourceId, final String type) {
        final Document filter = new Document("sourceId", sourceId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemRelationDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<WorkItemRelation> findByTargetAndType(final UUID targetId, final String type) {
        final Document filter = new Document("targetId", targetId.toString())
                .append("relationType", type)
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
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
        final MongoWorkItemRelationDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemRelationDocument::toDomain);
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

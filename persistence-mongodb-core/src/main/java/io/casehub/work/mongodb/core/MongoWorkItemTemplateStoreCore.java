package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoWorkItemTemplateDocument;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoWorkItemTemplateStoreCore implements WorkItemTemplateStore {

    private final MongoCollection<MongoWorkItemTemplateDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoWorkItemTemplateStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("work_item_templates", MongoWorkItemTemplateDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public WorkItemTemplate put(final WorkItemTemplate template) {
        if (template.id == null) {
            template.id = UUID.randomUUID();
        }
        if (template.createdAt == null) {
            template.createdAt = Instant.now();
        }
        if (template.tenancyId == null) {
            template.tenancyId = currentPrincipal.tenancyId();
        }

        MongoWorkItemTemplateDocument doc = MongoWorkItemTemplateDocument.from(template);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return template;
    }

    @Override
    public Optional<WorkItemTemplate> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemTemplateDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemTemplateDocument::toDomain);
    }

    @Override
    public Optional<WorkItemTemplate> getByName(final String name) {
        final Document filter = new Document("name", name)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemTemplateDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoWorkItemTemplateDocument::toDomain);
    }

    @Override
    public List<WorkItemTemplate> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoWorkItemTemplateDocument::toDomain)
                .sorted(Comparator.comparing(t -> t.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.mongodb.core.doc.MongoLabelRuleDocument;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoLabelRuleStoreCore implements LabelRuleStore {

    private final MongoCollection<MongoLabelRuleDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoLabelRuleStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("label_rules", MongoLabelRuleDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public LabelRuleEntity put(final LabelRuleEntity rule) {
        if (rule.id == null) {
            rule.id = UUID.randomUUID();
        }
        if (rule.createdAt == null) {
            rule.createdAt = Instant.now();
        }
        if (rule.tenancyId == null) {
            rule.tenancyId = currentPrincipal.tenancyId();
        }
        MongoLabelRuleDocument doc = MongoLabelRuleDocument.from(rule);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return rule;
    }

    @Override
    public Optional<LabelRuleEntity> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelRuleDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoLabelRuleDocument::toDomain);
    }

    @Override
    public List<LabelRuleEntity> findEnabled() {
        final Document filter = new Document("enabled", true)
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>()).stream()
                .map(MongoLabelRuleDocument::toDomain)
                .toList();
    }

    @Override
    public List<LabelRuleEntity> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>()).stream()
                .map(MongoLabelRuleDocument::toDomain)
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

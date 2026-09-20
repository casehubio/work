package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.work.mongodb.core.doc.MongoLabelDefinitionDocument;
import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoLabelDefinitionStoreCore implements LabelDefinitionStore {

    private final MongoCollection<MongoLabelDefinitionDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoLabelDefinitionStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("label_definitions", MongoLabelDefinitionDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public LabelDefinition put(final LabelDefinition definition) {
        if (definition.id == null) {
            definition.id = UUID.randomUUID();
        }
        if (definition.createdAt == null) {
            definition.createdAt = Instant.now();
        }
        if (definition.tenancyId == null) {
            definition.tenancyId = currentPrincipal.tenancyId();
        }

        MongoLabelDefinitionDocument doc = MongoLabelDefinitionDocument.from(definition);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return definition;
    }

    @Override
    public Optional<LabelDefinition> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelDefinitionDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoLabelDefinitionDocument::toDomain);
    }

    @Override
    public List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        final Document filter = new Document("vocabularyId", vocabularyId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoLabelDefinitionDocument::toDomain)
                .toList();
    }

    @Override
    public List<LabelDefinition> findByPath(final Path path) {
        final Document filter = new Document("path", path.value())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoLabelDefinitionDocument::toDomain)
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

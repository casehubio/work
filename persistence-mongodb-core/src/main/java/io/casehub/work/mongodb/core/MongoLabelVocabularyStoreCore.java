package io.casehub.work.mongodb.core;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.work.mongodb.core.doc.MongoLabelVocabularyDocument;
import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MongoLabelVocabularyStoreCore implements LabelVocabularyStore {

    private final MongoCollection<MongoLabelVocabularyDocument> collection;
    private final CurrentPrincipal currentPrincipal;

    public MongoLabelVocabularyStoreCore(MongoDatabase database, CurrentPrincipal currentPrincipal) {
        this.collection = database.getCollection("label_vocabularies", MongoLabelVocabularyDocument.class);
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public LabelVocabulary put(final LabelVocabulary vocabulary) {
        if (vocabulary.id == null) {
            vocabulary.id = UUID.randomUUID();
        }
        if (vocabulary.tenancyId == null) {
            vocabulary.tenancyId = currentPrincipal.tenancyId();
        }

        MongoLabelVocabularyDocument doc = MongoLabelVocabularyDocument.from(vocabulary);
        collection.replaceOne(new Document("_id", doc.id), doc, new ReplaceOptions().upsert(true));
        return vocabulary;
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelVocabularyDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoLabelVocabularyDocument::toDomain);
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        return collection.find(filter).into(new ArrayList<>())
                .stream()
                .map(MongoLabelVocabularyDocument::toDomain)
                .toList();
    }

    @Override
    public Optional<LabelVocabulary> findByScope(final Path scope) {
        final Document filter = new Document("scope", scope.value())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelVocabularyDocument doc = collection.find(filter).first();
        return Optional.ofNullable(doc).map(MongoLabelVocabularyDocument::toDomain);
    }

    @Override
    public LabelVocabulary findOrCreate(final Path scope, final String name) {
        final String scopeStr = scope.value();
        final String tenancyId = currentPrincipal.tenancyId();

        final Bson filter = Filters.and(
                Filters.eq("scope", scopeStr),
                Filters.eq("tenancyId", tenancyId));

        final UUID newId = UUID.randomUUID();
        final Bson update = Updates.setOnInsert(
                new Document("_id", newId.toString())
                        .append("scope", scopeStr)
                        .append("name", name)
                        .append("tenancyId", tenancyId));

        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER);

        final MongoLabelVocabularyDocument result = collection.findOneAndUpdate(filter, update, options);

        if (result == null) {
            throw new IllegalStateException("Unexpected null result from findOneAndUpdate with upsert");
        }

        return result.toDomain();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return collection.deleteOne(filter).getDeletedCount() > 0;
    }
}

package io.casehub.work.mongodb;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;

import io.casehub.work.runtime.model.LabelVocabulary;
import io.casehub.work.runtime.repository.LabelVocabularyStore;

/**
 * MongoDB implementation of {@link LabelVocabularyStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 *
 * <p>
 * The {@link #findOrCreate(Path, String)} method uses MongoDB's {@code findOneAndUpdate}
 * with {@code upsert: true} to provide atomic insert-if-absent semantics without race
 * conditions in clustered deployments.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoLabelVocabularyStore implements LabelVocabularyStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public LabelVocabulary put(final LabelVocabulary vocabulary) {
        if (vocabulary.id == null) {
            vocabulary.id = UUID.randomUUID();
        }
        if (vocabulary.tenancyId == null) {
            vocabulary.tenancyId = currentPrincipal.tenancyId();
        }

        MongoLabelVocabularyDocument.from(vocabulary).persistOrUpdate();
        return vocabulary;
    }

    @Override
    public Optional<LabelVocabulary> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelVocabularyDocument doc = MongoLabelVocabularyDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoLabelVocabularyDocument::toDomain);
    }

    @Override
    public List<LabelVocabulary> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        final List<MongoLabelVocabularyDocument> docs = MongoLabelVocabularyDocument.<MongoLabelVocabularyDocument> find(
                filter).list();
        return docs.stream()
                .map(MongoLabelVocabularyDocument::toDomain)
                .toList();
    }

    @Override
    public Optional<LabelVocabulary> findByScope(final Path scope) {
        final Document filter = new Document("scope", scope.value())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelVocabularyDocument doc = MongoLabelVocabularyDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoLabelVocabularyDocument::toDomain);
    }

    @Override
    public LabelVocabulary findOrCreate(final Path scope, final String name) {
        final String scopeStr = scope.value();
        final String tenancyId = currentPrincipal.tenancyId();

        // Filter: match scope + tenancyId
        final Bson filter = Filters.and(
                Filters.eq("scope", scopeStr),
                Filters.eq("tenancyId", tenancyId));

        // Update: $setOnInsert for all fields (only applied on insert)
        final UUID newId = UUID.randomUUID();
        final Bson update = Updates.setOnInsert(
                new Document("_id", newId.toString())
                        .append("scope", scopeStr)
                        .append("name", name)
                        .append("tenancyId", tenancyId));

        // Options: upsert + return document after modification
        final FindOneAndUpdateOptions options = new FindOneAndUpdateOptions()
                .upsert(true)
                .returnDocument(ReturnDocument.AFTER);

        final MongoLabelVocabularyDocument result = (MongoLabelVocabularyDocument)
                MongoLabelVocabularyDocument.mongoCollection()
                        .findOneAndUpdate(filter, update, options);

        if (result == null) {
            throw new IllegalStateException("Unexpected null result from findOneAndUpdate with upsert");
        }

        return result.toDomain();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoLabelVocabularyDocument.delete(filter) > 0;
    }
}

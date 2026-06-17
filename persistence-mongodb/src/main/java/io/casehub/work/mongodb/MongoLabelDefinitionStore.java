package io.casehub.work.mongodb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import org.bson.Document;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;

import io.casehub.work.runtime.model.LabelDefinition;
import io.casehub.work.runtime.repository.LabelDefinitionStore;

/**
 * MongoDB implementation of {@link LabelDefinitionStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoLabelDefinitionStore implements LabelDefinitionStore {

    @Inject
    CurrentPrincipal currentPrincipal;

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

        MongoLabelDefinitionDocument.from(definition).persistOrUpdate();
        return definition;
    }

    @Override
    public Optional<LabelDefinition> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelDefinitionDocument doc = MongoLabelDefinitionDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoLabelDefinitionDocument::toDomain);
    }

    @Override
    public List<LabelDefinition> findByVocabularyId(final UUID vocabularyId) {
        final Document filter = new Document("vocabularyId", vocabularyId.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoLabelDefinitionDocument> docs = MongoLabelDefinitionDocument.<MongoLabelDefinitionDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoLabelDefinitionDocument::toDomain)
                .toList();
    }

    @Override
    public List<LabelDefinition> findByPath(final Path path) {
        final Document filter = new Document("path", path.value())
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoLabelDefinitionDocument> docs = MongoLabelDefinitionDocument.<MongoLabelDefinitionDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoLabelDefinitionDocument::toDomain)
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoLabelDefinitionDocument.delete(filter) > 0;
    }
}

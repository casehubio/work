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

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemTemplateStore;

/**
 * MongoDB implementation of {@link WorkItemTemplateStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoWorkItemTemplateStore implements WorkItemTemplateStore {

    @Inject
    CurrentPrincipal currentPrincipal;

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

        MongoWorkItemTemplateDocument.from(template).persistOrUpdate();
        return template;
    }

    @Override
    public Optional<WorkItemTemplate> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemTemplateDocument doc = MongoWorkItemTemplateDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemTemplateDocument::toDomain);
    }

    @Override
    public Optional<WorkItemTemplate> getByName(final String name) {
        final Document filter = new Document("name", name)
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoWorkItemTemplateDocument doc = MongoWorkItemTemplateDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoWorkItemTemplateDocument::toDomain);
    }

    @Override
    public List<WorkItemTemplate> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        final List<MongoWorkItemTemplateDocument> docs = MongoWorkItemTemplateDocument.<MongoWorkItemTemplateDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoWorkItemTemplateDocument::toDomain)
                .sorted(Comparator.comparing(t -> t.name))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoWorkItemTemplateDocument.delete(filter) > 0;
    }
}

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
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;

@ApplicationScoped
@Alternative
@Priority(1)
public class MongoLabelRuleStore implements LabelRuleStore {

    @Inject
    CurrentPrincipal currentPrincipal;

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
        MongoLabelRuleDocument.from(rule).persistOrUpdate();
        return rule;
    }

    @Override
    public Optional<LabelRuleEntity> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoLabelRuleDocument doc = MongoLabelRuleDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoLabelRuleDocument::toDomain);
    }

    @Override
    public List<LabelRuleEntity> findEnabled() {
        final Document filter = new Document("enabled", true)
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoLabelRuleDocument.<MongoLabelRuleDocument>find(filter).list().stream()
                .map(MongoLabelRuleDocument::toDomain)
                .toList();
    }

    @Override
    public List<LabelRuleEntity> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        return MongoLabelRuleDocument.<MongoLabelRuleDocument>find(filter).list().stream()
                .map(MongoLabelRuleDocument::toDomain)
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoLabelRuleDocument.delete(filter) > 0;
    }
}

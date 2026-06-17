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

import io.casehub.work.runtime.filter.FilterRule;
import io.casehub.work.runtime.repository.FilterRuleStore;

/**
 * MongoDB implementation of {@link FilterRuleStore}.
 *
 * <p>
 * Selected by CDI over the default JPA store when this module is on the classpath.
 * All queries are tenant-scoped using {@link CurrentPrincipal#tenancyId()}.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class MongoFilterRuleStore implements FilterRuleStore {

    @Inject
    CurrentPrincipal currentPrincipal;

    @Override
    public FilterRule put(final FilterRule rule) {
        if (rule.id == null) {
            rule.id = UUID.randomUUID();
        }
        if (rule.createdAt == null) {
            rule.createdAt = Instant.now();
        }
        if (rule.tenancyId == null) {
            rule.tenancyId = currentPrincipal.tenancyId();
        }

        MongoFilterRuleDocument.from(rule).persistOrUpdate();
        return rule;
    }

    @Override
    public Optional<FilterRule> get(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        final MongoFilterRuleDocument doc = MongoFilterRuleDocument.find(filter).firstResult();
        return Optional.ofNullable(doc).map(MongoFilterRuleDocument::toDomain);
    }

    @Override
    public List<FilterRule> allEnabled() {
        final Document filter = new Document("enabled", true)
                .append("tenancyId", currentPrincipal.tenancyId());
        final List<MongoFilterRuleDocument> docs = MongoFilterRuleDocument.<MongoFilterRuleDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoFilterRuleDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public List<FilterRule> scanAll() {
        final Document filter = new Document("tenancyId", currentPrincipal.tenancyId());
        final List<MongoFilterRuleDocument> docs = MongoFilterRuleDocument.<MongoFilterRuleDocument> find(filter)
                .list();
        return docs.stream()
                .map(MongoFilterRuleDocument::toDomain)
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final Document filter = new Document("_id", id.toString())
                .append("tenancyId", currentPrincipal.tenancyId());
        return MongoFilterRuleDocument.delete(filter) > 0;
    }
}

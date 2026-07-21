package io.casehub.work.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.work.runtime.filter.LabelRuleEntity;
import io.casehub.work.runtime.repository.LabelRuleStore;

@ApplicationScoped
@Alternative
@Priority(100)
public class InMemoryLabelRuleStore implements LabelRuleStore {

    private final Map<UUID, LabelRuleEntity> store = new ConcurrentHashMap<>();

    @Inject
    CurrentPrincipal currentPrincipal;

    public void clear() {
        store.clear();
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
        store.put(rule.id, rule);
        return rule;
    }

    @Override
    public Optional<LabelRuleEntity> get(final UUID id) {
        final LabelRuleEntity rule = store.get(id);
        if (rule != null && currentPrincipal.tenancyId().equals(rule.tenancyId)) {
            return Optional.of(rule);
        }
        return Optional.empty();
    }

    @Override
    public List<LabelRuleEntity> findEnabled() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .filter(r -> r.enabled)
                .toList();
    }

    @Override
    public List<LabelRuleEntity> scanAll() {
        final String tenancyId = currentPrincipal.tenancyId();
        return store.values().stream()
                .filter(r -> tenancyId.equals(r.tenancyId))
                .toList();
    }

    @Override
    public boolean delete(final UUID id) {
        final LabelRuleEntity rule = store.get(id);
        if (rule != null && currentPrincipal.tenancyId().equals(rule.tenancyId)) {
            store.remove(id);
            return true;
        }
        return false;
    }
}
